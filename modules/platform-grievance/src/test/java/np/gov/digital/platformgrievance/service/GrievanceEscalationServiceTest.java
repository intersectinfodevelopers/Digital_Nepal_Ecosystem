package np.gov.digital.platformgrievance.service;

import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgrievance.dto.GrievanceEscalationRequest;
import np.gov.digital.platformgrievance.dto.GrievanceRejectionRequest;
import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.enums.GrievanceCategory;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.exception.GrievanceEscalationException;
import np.gov.digital.platformgrievance.exception.InvalidGrievanceTransitionException;
import np.gov.digital.platformgrievance.repository.GrievanceEventRepository;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrievanceEscalationServiceTest {

    @Mock GrievanceRepository grievanceRepository;
    @Mock GrievanceEventRepository grievanceEventRepository;
    @Mock GrievanceNotificationService notificationService;
    @Mock AuditLogService auditLogService;

    private GrievanceEscalationService service;

    private final UUID grievanceId    = UUID.randomUUID();
    private final UUID municipalityId = UUID.randomUUID();
    private final UUID otherMuniId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GrievanceEscalationService(
                grievanceRepository,
                grievanceEventRepository,
                notificationService,
                auditLogService
        );
    }

    private Grievance buildGrievance(GrievanceStatus status, UUID muniId) {
        return Grievance.builder()
                .id(grievanceId)
                .citizenId(UUID.randomUUID())
                .municipalityId(muniId)
                .trackingCode("GRV-2026-000001")
                .status(status)
                .category(GrievanceCategory.DATA_INACCURACY)
                .description("test")
                .filedAt(Instant.now())
                .slaDueAt(Instant.now().plusSeconds(3600))
                .slaBreached(false)
                .reopenCount((short) 0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── Escalation ──────────────────────────────────────────────────────────

    @Test
    void escalationAllowedWhenSameMunicipality() {
        Grievance g = buildGrievance(GrievanceStatus.IN_PROGRESS, municipalityId);
        when(grievanceRepository.findById(grievanceId)).thenReturn(Optional.of(g));
        when(grievanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = GrievanceEscalationRequest.builder()
                .municipalityId(municipalityId)
                .reason("Requires judicial review")
                .build();

        var response = service.escalateToJudicial(grievanceId, request, null);

        assertThat(response.getStatus()).isEqualTo(GrievanceStatus.REFERRED_JUDICIAL);
        verify(grievanceRepository).save(any());
        verify(grievanceEventRepository).save(any());
    }

    @Test
    void escalationBlockedWhenDifferentMunicipality() {
        // Day 12 checklist: "403 on cross-municipality attempt"
        Grievance g = buildGrievance(GrievanceStatus.IN_PROGRESS, municipalityId);
        when(grievanceRepository.findById(grievanceId)).thenReturn(Optional.of(g));

        var request = GrievanceEscalationRequest.builder()
                .municipalityId(otherMuniId) // different municipality
                .reason("Requires judicial review")
                .build();

        assertThatThrownBy(() ->
                service.escalateToJudicial(grievanceId, request, null))
                .isInstanceOf(GrievanceEscalationException.class)
                .hasMessageContaining("different municipality");

        verify(grievanceRepository, never()).save(any());
    }

    @Test
    void escalationFromReceivedStatusIsRejectedByStateMachine() {
        // RECEIVED → REFERRED_JUDICIAL is not allowed — must go through IN_PROGRESS first
        Grievance g = buildGrievance(GrievanceStatus.RECEIVED, municipalityId);
        when(grievanceRepository.findById(grievanceId)).thenReturn(Optional.of(g));

        var request = GrievanceEscalationRequest.builder()
                .municipalityId(municipalityId)
                .reason("reason")
                .build();

        assertThatThrownBy(() ->
                service.escalateToJudicial(grievanceId, request, null))
                .isInstanceOf(InvalidGrievanceTransitionException.class);
    }

    @Test
    void smsFailureDoesNotRollBackEscalation() {
        Grievance g = buildGrievance(GrievanceStatus.IN_PROGRESS, municipalityId);
        when(grievanceRepository.findById(grievanceId)).thenReturn(Optional.of(g));
        when(grievanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // SMS throws
        doThrow(new RuntimeException("SMS network error"))
                .when(notificationService)
                .notifyWardAdminOfEscalation(any(), any(), any(), any());

        var request = GrievanceEscalationRequest.builder()
                .municipalityId(municipalityId)
                .reason("reason")
                .build();

        // Should NOT throw — escalation committed, SMS failure swallowed
        assertThatNoException().isThrownBy(() ->
                service.escalateToJudicial(grievanceId, request, "9800000000"));

        verify(grievanceRepository).save(any());
    }

    // ── CLOSED_INVALID ───────────────────────────────────────────────────────

    @Test
    void closeInvalidRequiresReceivedOrInProgressStatus() {
        Grievance g = buildGrievance(GrievanceStatus.RECEIVED, municipalityId);
        when(grievanceRepository.findById(grievanceId)).thenReturn(Optional.of(g));
        when(grievanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = GrievanceRejectionRequest.builder()
                .rejectionReason("Not a valid grievance")
                .build();

        var response = service.closeInvalid(grievanceId, request, null);

        assertThat(response.getStatus()).isEqualTo(GrievanceStatus.CLOSED_INVALID);
        verify(grievanceRepository).save(any());
        verify(grievanceEventRepository).save(any());
    }

    @Test
    void closeInvalidSetsRejectionReason() {
        Grievance g = buildGrievance(GrievanceStatus.RECEIVED, municipalityId);
        when(grievanceRepository.findById(grievanceId)).thenReturn(Optional.of(g));
        when(grievanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = GrievanceRejectionRequest.builder()
                .rejectionReason("Duplicate grievance")
                .build();

        service.closeInvalid(grievanceId, request, null);

        verify(grievanceRepository).save(argThat(saved ->
                "Duplicate grievance".equals(((Grievance) saved).getRejectionReason())));
    }
}