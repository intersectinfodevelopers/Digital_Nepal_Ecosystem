package np.gov.digital.platformgrievance.scheduler;

import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.entity.GrievanceEvent;
import np.gov.digital.platformgrievance.enums.GrievanceCategory;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.repository.GrievanceEventRepository;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import np.gov.digital.platformgrievance.service.GrievanceNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaBreachDetectionJobTest {

    @Mock GrievanceRepository grievanceRepository;
    @Mock GrievanceEventRepository grievanceEventRepository;
    @Mock GrievanceNotificationService notificationService;

    private SlaBreachDetectionJob job;

    @BeforeEach
    void setUp() {
        job = new SlaBreachDetectionJob(
                grievanceRepository,
                grievanceEventRepository,
                notificationService);
    }

    private Grievance buildBreachedGrievance() {
        return Grievance.builder()
                .id(UUID.randomUUID())
                .citizenId(UUID.randomUUID())
                .municipalityId(UUID.randomUUID())
                .trackingCode("GRV-2026-000001")
                .status(GrievanceStatus.IN_PROGRESS)
                .category(GrievanceCategory.DATA_INACCURACY)
                .description("test")
                .filedAt(Instant.now().minusSeconds(172800))
                .slaDueAt(Instant.now().minusSeconds(3600))
                .slaBreached(false)
                .reopenCount((short) 0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void marksBreachedGrievancesInBulk() {
        Grievance g = buildBreachedGrievance();
        when(grievanceRepository.findUnflaggedBreachedGrievances(any(), anyList()))
                .thenReturn(List.of(g));

        job.detectSlaBreaches();

        verify(grievanceRepository).markSlaBreached(
                argThat(ids -> ids.contains(g.getId())),
                any(Instant.class));
    }

    @Test
    void writesOneEventLogEntryPerBreachedGrievance() {
        Grievance g1 = buildBreachedGrievance();
        Grievance g2 = buildBreachedGrievance();
        when(grievanceRepository.findUnflaggedBreachedGrievances(any(), anyList()))
                .thenReturn(List.of(g1, g2));

        job.detectSlaBreaches();

        verify(grievanceEventRepository, times(2)).save(any());
    }

    @Test
    void doesNothingWhenNoBreaches() {
        when(grievanceRepository.findUnflaggedBreachedGrievances(any(), anyList()))
                .thenReturn(List.of());

        job.detectSlaBreaches();

        verify(grievanceRepository, never()).markSlaBreached(any(), any());
        verify(grievanceEventRepository, never()).save(any());
    }

    @Test
    void eventLogHasCorrectEventTypeAndSystemRole() {
        Grievance g = buildBreachedGrievance();
        when(grievanceRepository.findUnflaggedBreachedGrievances(any(), anyList()))
                .thenReturn(List.of(g));

        job.detectSlaBreaches();

        ArgumentCaptor<GrievanceEvent> captor =
                ArgumentCaptor.forClass(GrievanceEvent.class);
        verify(grievanceEventRepository).save(captor.capture());

        assertThat(captor.getValue().getEventType()).isEqualTo("GRIEVANCE_SLA_BREACHED");
        assertThat(captor.getValue().getActorRole()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().getActorId()).isNull();
    }

    @Test
    void smsFailureDoesNotStopBreachMarking() {
        Grievance g = buildBreachedGrievance();
        when(grievanceRepository.findUnflaggedBreachedGrievances(any(), anyList()))
                .thenReturn(List.of(g));
        doThrow(new RuntimeException("SMS failed"))
                .when(notificationService)
                .notifyLocalBodyAdminOfSlaBreach(any(), any());

        // Should NOT throw — breach marking committed, SMS failure swallowed
        job.detectSlaBreaches();

        verify(grievanceRepository).markSlaBreached(any(), any());
    }
}