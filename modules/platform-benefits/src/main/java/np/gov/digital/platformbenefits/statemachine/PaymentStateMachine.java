package np.gov.digital.platformbenefits.statemachine;

import np.gov.digital.platformbenefits.enums.PaymentStatus;
import np.gov.digital.platformbenefits.exception.InvalidPaymentTransitionException;

import java.util.Map;
import java.util.Set;

import static np.gov.digital.platformbenefits.enums.PaymentStatus.*;

/**
 * PENDING -&gt; INITIATED -&gt; {SETTLED | FAILED}; PENDING/INITIATED can
 * also be CANCELLED before settlement. SETTLED/FAILED/CANCELLED are all
 * terminal — a wrongly settled or failed disbursement is corrected by a
 * fresh disbursement, never by mutating a decided one, same discipline
 * as the vital-event state machine.
 */
public final class PaymentStateMachine {

    private PaymentStateMachine() {}

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PENDING,    Set.of(INITIATED, CANCELLED),
            INITIATED,  Set.of(SETTLED, FAILED, CANCELLED),
            SETTLED,    Set.of(),
            FAILED,     Set.of(),
            CANCELLED,  Set.of()
    );

    public static void validate(PaymentStatus from, PaymentStatus to) {
        if (!isAllowed(from, to)) {
            throw new InvalidPaymentTransitionException(from, to);
        }
    }

    public static boolean isAllowed(PaymentStatus from, PaymentStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
