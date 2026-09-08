package np.gov.digital.platformvitalevents.enums;

/**
 * Shared state machine for all five vital event types (SDD Extended
 * Modules §4.1):
 *
 *   SUBMITTED → PENDING_APPROVAL → { APPROVED | REJECTED | CAO_REVIEW }
 *   CAO_REVIEW → { APPROVED | REJECTED }
 *
 * CAO_REVIEW is reached only two ways: the Ward/Local Body Admin escalates
 * it deliberately, or GrievanceEscalation-style auto-escalation moves it
 * there after 5 business days of no action (Governance Tiers §6) — never
 * skips straight to Province, never resolvable by the original submitter.
 * See VitalEventStateMachine for the enforced transition table.
 */
public enum VitalEventStatus {
    SUBMITTED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CAO_REVIEW
}
