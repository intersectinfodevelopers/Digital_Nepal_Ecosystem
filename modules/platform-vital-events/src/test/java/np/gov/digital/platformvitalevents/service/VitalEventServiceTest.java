package np.gov.digital.platformvitalevents.service;

import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;
import np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VitalEventServiceTest {

    @Mock private VitalEventRepository vitalEventRepository;
    @Mock private AuditLogService auditLogService;

    private VitalEventService vitalEventService;

    private UUID submitterId;
    private UUID approverId;
    private VitalEvent event;

    @BeforeEach
    void setUp() {
        vitalEventService = new VitalEventService(vitalEventRepository, auditLogService);

        submitterId = UUID.randomUUID();
        approverId = UUID.randomUUID();

        Ward ward = new Ward();
        event = VitalEvent.builder()
                .id(UUID.randomUUID())
                .eventType(VitalEventType.BIRTH)
                .status(VitalEventStatus.PENDING_APPROVAL)
                .ward(ward)
                .submittedBy(submitterId)
                .build();

        lenient().when(vitalEventRepository.save(any(VitalEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approve_happyPath_transitionsToApprovedAndStampsReviewer() {
        when(vitalEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        VitalEvent result = vitalEventService.approve(event.getId(), approverId);

        assertThat(result.getStatus()).isEqualTo(VitalEventStatus.APPROVED);
        assertThat(result.getReviewedBy()).isEqualTo(approverId);
        assertThat(result.getReviewedAt()).isNotNull();
        verify(auditLogService).log(any(), isNull(), anyString());
    }

    @Test
    void approve_bySameActorWhoSubmitted_throwsSelfApproval() {
        when(vitalEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> vitalEventService.approve(event.getId(), submitterId))
                .isInstanceOf(SelfApprovalException.class);

        verify(vitalEventRepository, never()).save(any());
    }

    @Test
    void reject_bySameActorWhoSubmitted_throwsSelfApproval() {
        when(vitalEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> vitalEventService.reject(event.getId(), submitterId, "bad data"))
                .isInstanceOf(SelfApprovalException.class);

        verify(vitalEventRepository, never()).save(any());
    }

    @Test
    void approve_alreadyApproved_throwsInvalidTransition() {
        event.setStatus(VitalEventStatus.APPROVED);
        when(vitalEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> vitalEventService.approve(event.getId(), approverId))
                .isInstanceOf(InvalidVitalEventTransitionException.class);
    }

    @Test
    void approve_unknownId_throwsVitalEventNotFound() {
        UUID missingId = UUID.randomUUID();
        when(vitalEventRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vitalEventService.approve(missingId, approverId))
                .isInstanceOf(VitalEventNotFoundException.class);
    }

    @Test
    void reject_happyPath_transitionsToRejectedWithReason() {
        when(vitalEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        VitalEvent result = vitalEventService.reject(event.getId(), approverId, "Missing document");

        assertThat(result.getStatus()).isEqualTo(VitalEventStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Missing document");
        assertThat(result.getReviewedBy()).isEqualTo(approverId);
    }

    @Test
    void escalateToCaoReview_automatic_stampsAutoEscalatedAt() {
        when(vitalEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        VitalEvent result = vitalEventService.escalateToCaoReview(event.getId(), true);

        assertThat(result.getStatus()).isEqualTo(VitalEventStatus.CAO_REVIEW);
        assertThat(result.getAutoEscalatedAt()).isNotNull();
    }

    @Test
    void escalateToCaoReview_manual_doesNotStampAutoEscalatedAt() {
        when(vitalEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        VitalEvent result = vitalEventService.escalateToCaoReview(event.getId(), false);

        assertThat(result.getStatus()).isEqualTo(VitalEventStatus.CAO_REVIEW);
        assertThat(result.getAutoEscalatedAt()).isNull();
    }

    @Test
    void submitForApproval_fromSubmitted_transitionsToPendingApproval() {
        event.setStatus(VitalEventStatus.SUBMITTED);

        VitalEvent result = vitalEventService.submitForApproval(event);

        assertThat(result.getStatus()).isEqualTo(VitalEventStatus.PENDING_APPROVAL);
    }
}
