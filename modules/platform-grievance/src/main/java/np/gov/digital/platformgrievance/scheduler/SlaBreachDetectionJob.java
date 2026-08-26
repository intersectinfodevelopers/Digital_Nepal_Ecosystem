package np.gov.digital.platformgrievance.scheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.entity.GrievanceEvent;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.repository.GrievanceEventRepository;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import np.gov.digital.platformgrievance.service.GrievanceNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaBreachDetectionJob {

    private final GrievanceRepository grievanceRepository;
    private final GrievanceEventRepository grievanceEventRepository;
    private final GrievanceNotificationService notificationService;

    private static final List<GrievanceStatus> CLOSED_STATUSES = List.of(
            GrievanceStatus.CLOSED,
            GrievanceStatus.CLOSED_INVALID
    );

    @Scheduled(cron = "0 0/30 * * * *")
    @Transactional
    public void detectSlaBreaches() {
        Instant now = Instant.now();
        log.info("SlaBreachDetectionJob: running at {}", now);

        List<Grievance> breached = grievanceRepository
                .findUnflaggedBreachedGrievances(now, CLOSED_STATUSES);

        if (breached.isEmpty()) {
            log.info("SlaBreachDetectionJob: no new SLA breaches found");
            return;
        }

        log.warn("SlaBreachDetectionJob: {} grievance(s) have breached 48h SLA",
                breached.size());

        // Bulk-flag all as breached in one query
        List<UUID> breachedIds = breached.stream()
                .map(Grievance::getId)
                .toList();
        grievanceRepository.markSlaBreached(breachedIds, now);

        // Write event log + alert per breach
        for (Grievance g : breached) {

            // Append event log — mirrors citizen_events pattern
            grievanceEventRepository.save(GrievanceEvent.builder()
                    .grievanceId(g.getId())
                    .citizenId(g.getCitizenId())
                    .eventType("GRIEVANCE_SLA_BREACHED")
                    .oldStatus(g.getStatus().name())
                    .newStatus(g.getStatus().name())
                    .actorId(null)
                    .actorRole("SYSTEM")
                    .note("48-hour SLA breached. Due at: " + g.getSlaDueAt())
                    .createdAt(now)
                    .build());

            // In-app alert — structured log picked up by monitoring
            log.warn("SLA_BREACH_ALERT grievance={} municipality={} status={} dueAt={}",
                    g.getTrackingCode(),
                    g.getMunicipalityId(),
                    g.getStatus(),
                    g.getSlaDueAt());

            try {
                notificationService.notifyLocalBodyAdminOfSlaBreach(
                        g.getTrackingCode(),
                        g.getMunicipalityId()
                );
            } catch (Exception e) {
                log.error("SlaBreachDetectionJob: SMS alert failed for {} — {}",
                        g.getTrackingCode(), e.getMessage());
            }
        }

        log.info("SlaBreachDetectionJob: marked {} grievance(s) as SLA breached",
                breached.size());
    }
}