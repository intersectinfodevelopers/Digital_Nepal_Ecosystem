package np.gov.digital.platformvitalevents.statemachine;

import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException;

import java.util.Map;
import java.util.Set;

import static np.gov.digital.platformvitalevents.enums.VitalEventStatus.*;

/**
 * Enforces SDD Extended Modules §4.1's shared state machine, reused by all
 * five vital event types:
 *
 *   SUBMITTED → PENDING_APPROVAL → { APPROVED | REJECTED | CAO_REVIEW }
 *   CAO_REVIEW → { APPROVED | REJECTED }
 *
 * Terminal states (APPROVED, REJECTED) have no further transitions — once a
 * vital event is decided, it is decided. There is deliberately no path back
 * to SUBMITTED or PENDING_APPROVAL from CAO_REVIEW: escalation only ever
 * moves forward toward a decision, per Governance Tiers §6 ("never skips
 * to Province, never blockable by Local Body Admin unavailability").
 */
public final class VitalEventStateMachine {

    private VitalEventStateMachine() {}

    private static final Map<VitalEventStatus, Set<VitalEventStatus>> ALLOWED = Map.of(
            SUBMITTED,         Set.of(PENDING_APPROVAL),
            PENDING_APPROVAL,  Set.of(APPROVED, REJECTED, CAO_REVIEW),
            CAO_REVIEW,        Set.of(APPROVED, REJECTED),
            APPROVED,          Set.of(),
            REJECTED,          Set.of()
    );

    public static void validate(VitalEventStatus from, VitalEventStatus to) {
        if (!isAllowed(from, to)) {
            throw new InvalidVitalEventTransitionException(from, to);
        }
    }

    public static boolean isAllowed(VitalEventStatus from, VitalEventStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(VitalEventStatus status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}
