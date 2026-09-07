package np.gov.digital.platformvitalevents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import np.gov.digital.platformvitalevents.statemachine.VitalEventStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared workflow logic for all five vital event types (SDD Extended
 * Modules §4.1). A concrete service (BirthRegistrationService today;
 * DeathRegistrationService etc. as they land) owns creating its own
 * vital_event + detail row and any event-specific post-approval action
 * (e.g. birth creates a citizen, death archives one) — this class owns
 * only the state machine, self-approval rule, and the generic audit
 * trail every event type shares.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VitalEventService {

    private final VitalEventRepository vitalEventRepository;
    private final AuditLogService auditLogService;

    public VitalEvent getOrThrow(UUID vitalEventId) {
        return vitalEventRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));
    }

    /**
     * SUBMITTED -> PENDING_APPROVAL. Called immediately after a concrete
     * service persists a new event — there is currently no separate
     * "citizen self-submits, Ward Admin later forwards" step implemented,
     * so the ward-level submission call is itself what makes an event
     * ready for Local Body Admin review.
     */
    @Transactional
    public VitalEvent submitForApproval(VitalEvent event) {
        VitalEventStateMachine.validate(event.getStatus(), VitalEventStatus.PENDING_APPROVAL);
        event.setStatus(VitalEventStatus.PENDING_APPROVAL);
        VitalEvent saved = vitalEventRepository.save(event);

        auditLogService.log(
                AuditEventType.VITAL_EVENT_SUBMITTED,
                null,
                saved.getEventType() + " event submitted for approval — ward: " + saved.getWard().getId()
        );
        return saved;
    }

    /**
     * PENDING_APPROVAL/CAO_REVIEW -> APPROVED. Only validates and applies
     * the generic transition — the caller (a concrete service) is
     * responsible for any event-specific side effect (creating a citizen,
     * archiving one, etc.) and should do so in the same transaction.
     */
    @Transactional
    public VitalEvent approve(UUID vitalEventId, UUID approverId) {
        VitalEvent event = getOrThrow(vitalEventId);
        requireNotSelfDecision(event, approverId);
        VitalEventStateMachine.validate(event.getStatus(), VitalEventStatus.APPROVED);

        event.setStatus(VitalEventStatus.APPROVED);
        event.setReviewedBy(approverId);
        event.setReviewedAt(Instant.now());
        VitalEvent saved = vitalEventRepository.save(event);

        auditLogService.log(
                AuditEventType.VITAL_EVENT_APPROVED,
                null,
                saved.getEventType() + " event approved — id: " + saved.getId()
        );
        return saved;
    }

    /** PENDING_APPROVAL/CAO_REVIEW -> REJECTED. */
    @Transactional
    public VitalEvent reject(UUID vitalEventId, UUID approverId, String reason) {
        VitalEvent event = getOrThrow(vitalEventId);
        requireNotSelfDecision(event, approverId);
        VitalEventStateMachine.validate(event.getStatus(), VitalEventStatus.REJECTED);

        event.setStatus(VitalEventStatus.REJECTED);
        event.setReviewedBy(approverId);
        event.setReviewedAt(Instant.now());
        event.setRejectionReason(reason);
        VitalEvent saved = vitalEventRepository.save(event);

        auditLogService.log(
                AuditEventType.VITAL_EVENT_REJECTED,
                null,
                saved.getEventType() + " event rejected — id: " + saved.getId() + ", reason: " + reason
        );
        return saved;
    }

    /**
     * PENDING_APPROVAL -> CAO_REVIEW. Used both by a deliberate manual
     * escalation and by the (not yet built) auto-escalation job — auto
     * escalation additionally stamps autoEscalatedAt so the two are
     * distinguishable later.
     */
    @Transactional
    public VitalEvent escalateToCaoReview(UUID vitalEventId, boolean automatic) {
        VitalEvent event = getOrThrow(vitalEventId);
        VitalEventStateMachine.validate(event.getStatus(), VitalEventStatus.CAO_REVIEW);

        event.setStatus(VitalEventStatus.CAO_REVIEW);
        if (automatic) {
            event.setAutoEscalatedAt(Instant.now());
        }
        VitalEvent saved = vitalEventRepository.save(event);

        auditLogService.log(
                AuditEventType.VITAL_EVENT_ESCALATED,
                null,
                saved.getEventType() + " event escalated to CAO_REVIEW (" +
                        (automatic ? "automatic — 5 business days unactioned" : "manual") + ") — id: " + saved.getId()
        );
        return saved;
    }

    // Governance Tiers §3 (no-self-approval) applied to the vital-events
    // workflow: whoever submitted an event can never be the one who
    // decides it, for either outcome — not just approval.
    private void requireNotSelfDecision(VitalEvent event, UUID actorId) {
        if (actorId != null && actorId.equals(event.getSubmittedBy())) {
            throw new SelfApprovalException(
                    "Cannot approve or reject a vital event you submitted yourself.");
        }
    }
}
