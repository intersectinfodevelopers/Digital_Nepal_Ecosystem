package np.gov.digital.platformvitalevents.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import np.gov.digital.platformvitalevents.service.VitalEventService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Governance Tiers §6 — auto-escalation: a ward submission un-acted-on
 * for 5 business days moves PENDING_APPROVAL -&gt; CAO_REVIEW
 * automatically. Never skips to Province, never blockable by Local Body
 * Admin unavailability — this job is the only thing that makes that
 * guarantee real; without it, "5 business days" is just a sentence in
 * the design doc.
 *
 * Runs hourly (frequent enough that no event sits un-escalated for long
 * after crossing the threshold, cheap enough that an hourly full-table
 * sweep against a PENDING_APPROVAL-filtered, indexed query is a non-issue
 * at any realistic scale — see idx_vital_event_status_submitted, V33).
 *
 * KNOWN LIMITATION: escalations this job performs are NOT attributed in
 * citizen_events. AuditLogService.log() requires an authenticated actor
 * on the SecurityContext (see its class Javadoc: "System-triggered
 * events must run as a system-service account, not anonymously") — but
 * no such reserved system-service account/principal exists anywhere in
 * this codebase yet. Rather than fabricate one without the rest of the
 * infrastructure that should come with it (a real system-service user
 * row, a principal type distinct from a real admin's, its own audit
 * trail conventions), auto-escalation currently degrades safely: the
 * vital_event row itself is updated and its own auto_escalated_at
 * timestamp is the durable record of when and that it happened, but
 * AuditLogService's own guard silently skips the citizen_events entry
 * (logged at WARN, not lost data, but not in the audit trail either).
 * Introducing a real system-service account is tracked as a follow-up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VitalEventAutoEscalationJob {

    private static final int ESCALATION_THRESHOLD_BUSINESS_DAYS = 5;

    // Generous calendar-day pre-filter for the DB query — see class
    // Javadoc's business-day reasoning: 5 business days in Nepal's
    // 6-workday week always elapses within 6 calendar days, never more,
    // so anything submitted less than 4 calendar days ago cannot
    // possibly qualify yet and is safely excluded before the precise
    // per-row business-day check below.
    private static final int CANDIDATE_PRE_FILTER_CALENDAR_DAYS = 4;

    private final VitalEventRepository vitalEventRepository;
    private final VitalEventService vitalEventService;

    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void escalateOverdueEvents() {
        Instant cutoff = Instant.now().minus(CANDIDATE_PRE_FILTER_CALENDAR_DAYS, ChronoUnit.DAYS);
        List<VitalEvent> candidates =
                vitalEventRepository.findByStatusAndSubmittedAtBefore(VitalEventStatus.PENDING_APPROVAL, cutoff);

        Instant now = Instant.now();
        int escalated = 0;
        for (VitalEvent event : candidates) {
            if (BusinessDayCalculator.hasElapsed(event.getSubmittedAt(), now, ESCALATION_THRESHOLD_BUSINESS_DAYS)) {
                try {
                    vitalEventService.escalateToCaoReview(event.getId(), true);
                    escalated++;
                } catch (Exception e) {
                    // One bad row must never stop the sweep from
                    // escalating everything else that's overdue.
                    log.error("VitalEventAutoEscalationJob: failed to escalate vitalEventId={}: {}",
                            event.getId(), e.getMessage(), e);
                }
            }
        }

        if (escalated > 0) {
            log.info("VitalEventAutoEscalationJob: escalated {} of {} candidate event(s) to CAO_REVIEW",
                    escalated, candidates.size());
        } else {
            log.debug("VitalEventAutoEscalationJob: no events due for escalation ({} candidate(s) checked)",
                    candidates.size());
        }
    }
}
