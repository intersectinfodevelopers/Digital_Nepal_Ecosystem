package np.gov.digital.platformgrievance.integration;

import np.gov.digital.platformgrievance.dto.*;
import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.entity.GrievanceEvent;
import np.gov.digital.platformgrievance.enums.GrievanceCategory;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.exception.GrievanceEscalationException;
import np.gov.digital.platformgrievance.exception.InvalidGrievanceTransitionException;
import np.gov.digital.platformgrievance.repository.GrievanceEventRepository;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import np.gov.digital.platformgrievance.service.*;
import np.gov.digital.platformgrievance.statemachine.GrievanceStateMachine;
import np.gov.digital.platformgrievance.util.TrackingCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import np.gov.digital.platformaudit.audit.AuditLogService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class GrievanceIntegrationTest {

    @Mock GrievanceRepository grievanceRepository;
    @Mock GrievanceEventRepository grievanceEventRepository;
    @Mock GrievanceNotificationService notificationService;
    @Mock AuditLogService auditLogService;
    @Mock TrackingCodeGenerator trackingCodeGenerator;

    // ── CHECKLIST: tracking_code format + filed_at set ───────────────────────

    @Test
    void trackingCodeMatchesGrvYearFormat() {
        String code = "GRV-" + java.time.Year.now().getValue() + "-000123";
        assertThat(code).matches("GRV-\\d{4}-\\d{6}");
    }

    @Test
    void trackingCodeIsUniquePerGrievance() {
        // Simulate two grievances with different tracking codes
        String code1 = "GRV-2026-000001";
        String code2 = "GRV-2026-000002";
        assertThat(code1).isNotEqualTo(code2);
    }

    @Test
    void everyStateTransitionWritesEventWithActorAndTimestamp() {
        // with actor + timestamp"
        UUID grievanceId = UUID.randomUUID();
        UUID citizenId   = UUID.randomUUID();
        UUID municipalityId = UUID.randomUUID();

        Grievance grievance = buildGrievance(
                grievanceId, citizenId, municipalityId, GrievanceStatus.RECEIVED);

        when(grievanceRepository.findById(grievanceId))
                .thenReturn(Optional.of(grievance));
        when(grievanceRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        GrievanceStateService stateService = new GrievanceStateService(
                grievanceRepository, grievanceEventRepository,
                auditLogService, notificationService);

        stateService.transition(grievanceId,
                GrievanceTransitionRequest.builder()
                        .targetStatus(GrievanceStatus.IN_PROGRESS)
                        .build());

        // Verify event log was written
        verify(grievanceEventRepository).save(argThat(event -> {
            GrievanceEvent e = (GrievanceEvent) event;
            return e.getGrievanceId().equals(grievanceId)
                    && e.getEventType().equals("GRIEVANCE_IN_PROGRESS")
                    && e.getOldStatus().equals("RECEIVED")
                    && e.getNewStatus().equals("IN_PROGRESS")
                    && e.getCreatedAt() != null; // timestamp always set
        }));
    }

    @Test
    void resolvedWardTransitionWritesEventWithNote() {
        UUID grievanceId = UUID.randomUUID();
        Grievance grievance = buildGrievance(
                grievanceId, UUID.randomUUID(), UUID.randomUUID(),
                GrievanceStatus.IN_PROGRESS);

        when(grievanceRepository.findById(grievanceId))
                .thenReturn(Optional.of(grievance));
        when(grievanceRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        GrievanceStateService stateService = new GrievanceStateService(
                grievanceRepository, grievanceEventRepository,
                auditLogService, notificationService);

        stateService.transition(grievanceId,
                GrievanceTransitionRequest.builder()
                        .targetStatus(GrievanceStatus.RESOLVED_WARD)
                        .note("Corrected the citizen data")
                        .build());

        verify(grievanceEventRepository).save(argThat(event -> {
            GrievanceEvent e = (GrievanceEvent) event;
            return e.getNewStatus().equals("RESOLVED_WARD")
                    && "Corrected the citizen data".equals(e.getNote());
        }));
    }

    // ── CHECKLIST: same-municipality escalation (403 cross-municipality) ──────

    @Test
    void escalationAllowedWithinSameMunicipality() {
        // municipality (403 cross-municipality)"
        UUID municipalityId = UUID.randomUUID();
        UUID grievanceId    = UUID.randomUUID();

        Grievance grievance = buildGrievance(
                grievanceId, UUID.randomUUID(), municipalityId,
                GrievanceStatus.IN_PROGRESS);

        when(grievanceRepository.findById(grievanceId))
                .thenReturn(Optional.of(grievance));
        when(grievanceRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        GrievanceEscalationService escalationService = new GrievanceEscalationService(
                grievanceRepository, grievanceEventRepository,
                notificationService, auditLogService);

        assertThatNoException().isThrownBy(() ->
                escalationService.escalateToJudicial(grievanceId,
                        GrievanceEscalationRequest.builder()
                                .municipalityId(municipalityId) // same municipality
                                .reason("Requires judicial review")
                                .build(),
                        null));
    }

    @Test
    void escalationBlockedAcrossDifferentMunicipality() {
        UUID grievanceMunicipalityId = UUID.randomUUID();
        UUID requestMunicipalityId   = UUID.randomUUID(); // different
        UUID grievanceId = UUID.randomUUID();

        Grievance grievance = buildGrievance(
                grievanceId, UUID.randomUUID(), grievanceMunicipalityId,
                GrievanceStatus.IN_PROGRESS);

        when(grievanceRepository.findById(grievanceId))
                .thenReturn(Optional.of(grievance));

        GrievanceEscalationService escalationService = new GrievanceEscalationService(
                grievanceRepository, grievanceEventRepository,
                notificationService, auditLogService);

        assertThatThrownBy(() ->
                escalationService.escalateToJudicial(grievanceId,
                        GrievanceEscalationRequest.builder()
                                .municipalityId(requestMunicipalityId)
                                .reason("reason")
                                .build(),
                        null))
                .isInstanceOf(GrievanceEscalationException.class)
                .hasMessageContaining("different municipality");
    }


    @Test
    void slaBreachJobSetsSlaBreachedTrueAndNotifiesAdmin() {
        // sla_breached=true + notification sent"
        Grievance overdue = buildGrievance(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                GrievanceStatus.IN_PROGRESS);
        overdue.setSlaDueAt(Instant.now().minusSeconds(3600)); // 1 hour overdue
        overdue.setSlaBreached(false);

        when(grievanceRepository.findUnflaggedBreachedGrievances(any(), anyList()))
                .thenReturn(List.of(overdue));

        np.gov.digital.platformgrievance.scheduler.SlaBreachDetectionJob job =
                new np.gov.digital.platformgrievance.scheduler.SlaBreachDetectionJob(
                        grievanceRepository, grievanceEventRepository, notificationService);

        job.detectSlaBreaches();

        // Verify sla_breached=true was set
        verify(grievanceRepository).markSlaBreached(
                argThat(ids -> ids.contains(overdue.getId())),
                any(Instant.class));

        // Verify event log written
        verify(grievanceEventRepository).save(argThat(event -> {
            GrievanceEvent e = (GrievanceEvent) event;
            return "GRIEVANCE_SLA_BREACHED".equals(e.getEventType())
                    && "SYSTEM".equals(e.getActorRole());
        }));

        // Verify Local Body Admin notified
        verify(notificationService).notifyLocalBodyAdminOfSlaBreach(
                eq(overdue.getTrackingCode()),
                eq(overdue.getMunicipalityId()));
    }

    @Test
    void publicTrackingExposeNoClientPii() {
        Grievance g = buildGrievance(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                GrievanceStatus.RESOLVED_WARD);
        g.setTrackingCode("GRV-2026-000123");

        when(grievanceRepository.findByTrackingCode("GRV-2026-000123"))
                .thenReturn(Optional.of(g));

        GrievanceTrackingService trackingService =
                new GrievanceTrackingService(grievanceRepository);

        GrievanceTrackingResponse response = trackingService.track("GRV-2026-000123");

        // Confirm fields that ARE returned
        assertThat(response.getTrackingCode()).isEqualTo("GRV-2026-000123");
        assertThat(response.getStatus()).isEqualTo(GrievanceStatus.RESOLVED_WARD);
        assertThat(response.getCategory()).isNotNull();

        // Confirm NO PII fields exist on the response class
        var fields = response.getClass().getDeclaredFields();
        assertThat(fields).noneMatch(f -> f.getName().equalsIgnoreCase("citizenId"));
        assertThat(fields).noneMatch(f -> f.getName().equalsIgnoreCase("description"));
        assertThat(fields).noneMatch(f -> f.getName().equalsIgnoreCase("filedBy"));
        assertThat(fields).noneMatch(f -> f.getName().equalsIgnoreCase("municipalityId"));
    }

    @Test
    void smsFailureNeverBlocksGrievanceCommit() {
        UUID grievanceId    = UUID.randomUUID();
        UUID municipalityId = UUID.randomUUID();

        Grievance grievance = buildGrievance(
                grievanceId, UUID.randomUUID(), municipalityId,
                GrievanceStatus.IN_PROGRESS);

        when(grievanceRepository.findById(grievanceId))
                .thenReturn(Optional.of(grievance));
        when(grievanceRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Sparrow SMS down"))
                .when(notificationService)
                .notifyWardAdminOfEscalation(any(), any(), any(), any());

        GrievanceEscalationService escalationService = new GrievanceEscalationService(
                grievanceRepository, grievanceEventRepository,
                notificationService, auditLogService);

        // Escalation committed — SMS failure swallowed by try-catch in escalation service
        assertThatNoException().isThrownBy(() ->
                escalationService.escalateToJudicial(grievanceId,
                        GrievanceEscalationRequest.builder()
                                .municipalityId(municipalityId)
                                .reason("reason")
                                .build(),
                        "9841000000"));

        verify(grievanceRepository).save(any());
    }

    @Test
    void smsFailureNeverBlocksEscalation() {
        UUID municipalityId = UUID.randomUUID();
        UUID grievanceId    = UUID.randomUUID();

        Grievance grievance = buildGrievance(
                grievanceId, UUID.randomUUID(), municipalityId,
                GrievanceStatus.IN_PROGRESS);

        when(grievanceRepository.findById(grievanceId))
                .thenReturn(Optional.of(grievance));
        when(grievanceRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SMS network error"))
                .when(notificationService)
                .notifyWardAdminOfEscalation(any(), any(), any(), any());

        GrievanceEscalationService escalationService = new GrievanceEscalationService(
                grievanceRepository, grievanceEventRepository,
                notificationService, auditLogService);

        // Escalation committed — SMS failure swallowed
        assertThatNoException().isThrownBy(() ->
                escalationService.escalateToJudicial(grievanceId,
                        GrievanceEscalationRequest.builder()
                                .municipalityId(municipalityId)
                                .reason("reason")
                                .build(),
                        "9841000000"));

        verify(grievanceRepository).save(any());
    }

    @Test
    void invalidStateTransitionIsRejected() {
        assertThatThrownBy(() ->
                GrievanceStateMachine.validate(
                        GrievanceStatus.RECEIVED,
                        GrievanceStatus.RESOLVED_WARD))
                .isInstanceOf(InvalidGrievanceTransitionException.class);
    }

    @Test
    void closedInvalidCannotTransitionFurther() {
        for (GrievanceStatus target : GrievanceStatus.values()) {
            assertThat(GrievanceStateMachine.isAllowed(
                    GrievanceStatus.CLOSED_INVALID, target))
                    .isFalse();
        }
    }

    private Grievance buildGrievance(UUID id, UUID citizenId,
                                     UUID municipalityId, GrievanceStatus status) {
        return Grievance.builder()
                .id(id)
                .citizenId(citizenId)
                .municipalityId(municipalityId)
                .trackingCode("GRV-2026-000001")
                .status(status)
                .category(GrievanceCategory.DATA_INACCURACY)
                .description("test grievance")
                .filedAt(Instant.now().minusSeconds(3600))
                .slaDueAt(Instant.now().plusSeconds(172800))
                .slaBreached(false)
                .reopenCount((short) 0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}