package np.gov.digital.platformevidenceexchange.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformevidenceexchange.entity.EvidenceRequest;
import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;
import np.gov.digital.platformevidenceexchange.repository.EvidenceRequestRepository;
import np.gov.digital.platformevidenceexchange.statemachine.EvidenceRequestStateMachine;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * A PENDING evidence request whose target agency never responds within
 * its own expiresAt (30 days from creation) moves to EXPIRED — never
 * left open indefinitely, and never resolved by the requester's own
 * action (that would let a Ward/Local Body Admin quietly mark their own
 * unanswered request as something it isn't).
 *
 * Same infrastructure decision as VitalEventAutoEscalationJob: plain
 * Spring @Scheduled, not Quartz — see that job's own class Javadoc for
 * why. Same known audit-trail limitation too: this job runs
 * unauthenticated, so AuditLogService.log() finds no actor and silently
 * skips the citizen_events entry (by design) — the row's own status/
 * updatedAt is still the durable record.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EvidenceRequestExpiryJob {

    private final EvidenceRequestRepository evidenceRequestRepository;

    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 90 * 1000L)
    @Transactional
    public void expireOverdueRequests() {
        Instant now = Instant.now();
        var overdue = evidenceRequestRepository.findByStatusAndExpiresAtBefore(
                EvidenceRequestStatus.PENDING, now);

        int expired = 0;
        for (EvidenceRequest request : overdue) {
            try {
                EvidenceRequestStateMachine.validate(request.getStatus(), EvidenceRequestStatus.EXPIRED);
                request.setStatus(EvidenceRequestStatus.EXPIRED);
                evidenceRequestRepository.save(request);
                expired++;
            } catch (Exception e) {
                log.error("EvidenceRequestExpiryJob: failed to expire requestId={}: {}",
                        request.getId(), e.getMessage(), e);
            }
        }

        if (expired > 0) {
            log.info("EvidenceRequestExpiryJob: expired {} of {} overdue request(s)", expired, overdue.size());
        } else {
            log.debug("EvidenceRequestExpiryJob: no requests due for expiry ({} checked)", overdue.size());
        }
    }
}
