package np.gov.digital.platformvitalevents.job;

import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import np.gov.digital.platformvitalevents.service.VitalEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VitalEventAutoEscalationJobTest {

    @Mock private VitalEventRepository vitalEventRepository;
    @Mock private VitalEventService vitalEventService;

    private VitalEventAutoEscalationJob job;

    @BeforeEach
    void setUp() {
        job = new VitalEventAutoEscalationJob(vitalEventRepository, vitalEventService);
    }

    private VitalEvent eventSubmittedDaysAgo(long calendarDays) {
        return VitalEvent.builder()
                .id(UUID.randomUUID())
                .eventType(VitalEventType.BIRTH)
                .status(VitalEventStatus.PENDING_APPROVAL)
                .ward(new Ward())
                .submittedBy(UUID.randomUUID())
                .submittedAt(Instant.now().minus(calendarDays, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void escalatesAnEventSubmittedWellOverTheThreshold() {
        // 10 calendar days ago is unambiguously >= 5 business days ago
        // regardless of which day of the week "now" falls on.
        VitalEvent overdue = eventSubmittedDaysAgo(10);
        when(vitalEventRepository.findByStatusAndSubmittedAtBefore(eq(VitalEventStatus.PENDING_APPROVAL), any()))
                .thenReturn(List.of(overdue));

        job.escalateOverdueEvents();

        verify(vitalEventService).escalateToCaoReview(overdue.getId(), true);
    }

    @Test
    void doesNotEscalateAnEventSubmittedWellUnderTheThreshold() {
        // 1 calendar day ago can never be 5 business days regardless of
        // weekday alignment.
        VitalEvent recent = eventSubmittedDaysAgo(1);
        when(vitalEventRepository.findByStatusAndSubmittedAtBefore(eq(VitalEventStatus.PENDING_APPROVAL), any()))
                .thenReturn(List.of(recent));

        job.escalateOverdueEvents();

        verify(vitalEventService, never()).escalateToCaoReview(any(), anyBoolean());
    }

    @Test
    void oneFailingEscalationDoesNotStopTheRestOfTheSweep() {
        VitalEvent first = eventSubmittedDaysAgo(10);
        VitalEvent second = eventSubmittedDaysAgo(10);
        when(vitalEventRepository.findByStatusAndSubmittedAtBefore(eq(VitalEventStatus.PENDING_APPROVAL), any()))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(vitalEventService).escalateToCaoReview(first.getId(), true);

        job.escalateOverdueEvents();

        verify(vitalEventService).escalateToCaoReview(first.getId(), true);
        verify(vitalEventService).escalateToCaoReview(second.getId(), true);
    }

    @Test
    void noCandidatesMeansNoEscalationCalls() {
        when(vitalEventRepository.findByStatusAndSubmittedAtBefore(eq(VitalEventStatus.PENDING_APPROVAL), any()))
                .thenReturn(List.of());

        job.escalateOverdueEvents();

        verifyNoInteractions(vitalEventService);
    }
}
