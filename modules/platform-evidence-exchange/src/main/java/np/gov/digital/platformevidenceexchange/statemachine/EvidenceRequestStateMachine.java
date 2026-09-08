package np.gov.digital.platformevidenceexchange.statemachine;

import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;
import np.gov.digital.platformevidenceexchange.exception.InvalidEvidenceRequestTransitionException;

import java.util.Map;
import java.util.Set;

import static np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus.*;

/**
 * PENDING -&gt; {RESPONDED | FAILED | EXPIRED}; all three are terminal.
 * EXPIRED is reachable only via EvidenceRequestExpiryJob (never a role's
 * own action) — enforced by that job being the only caller of the
 * transition, not by anything in this class itself, since the state
 * machine has no notion of "who" is asking.
 */
public final class EvidenceRequestStateMachine {

    private EvidenceRequestStateMachine() {}

    private static final Map<EvidenceRequestStatus, Set<EvidenceRequestStatus>> ALLOWED = Map.of(
            PENDING,    Set.of(RESPONDED, FAILED, EXPIRED),
            RESPONDED,  Set.of(),
            FAILED,     Set.of(),
            EXPIRED,    Set.of()
    );

    public static void validate(EvidenceRequestStatus from, EvidenceRequestStatus to) {
        if (!isAllowed(from, to)) {
            throw new InvalidEvidenceRequestTransitionException(from, to);
        }
    }

    public static boolean isAllowed(EvidenceRequestStatus from, EvidenceRequestStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
