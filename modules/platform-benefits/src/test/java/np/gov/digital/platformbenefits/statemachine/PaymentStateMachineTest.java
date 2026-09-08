package np.gov.digital.platformbenefits.statemachine;

import np.gov.digital.platformbenefits.exception.InvalidPaymentTransitionException;
import org.junit.jupiter.api.Test;

import static np.gov.digital.platformbenefits.enums.PaymentStatus.*;
import static org.assertj.core.api.Assertions.*;

class PaymentStateMachineTest {

    @Test
    void pendingToInitiatedIsAllowed() {
        assertThatNoException().isThrownBy(() -> PaymentStateMachine.validate(PENDING, INITIATED));
    }

    @Test
    void initiatedToSettledIsAllowed() {
        assertThat(PaymentStateMachine.isAllowed(INITIATED, SETTLED)).isTrue();
    }

    @Test
    void initiatedToFailedIsAllowed() {
        assertThat(PaymentStateMachine.isAllowed(INITIATED, FAILED)).isTrue();
    }

    @Test
    void pendingToCancelledIsAllowed() {
        assertThat(PaymentStateMachine.isAllowed(PENDING, CANCELLED)).isTrue();
    }

    @Test
    void pendingCannotSkipDirectlyToSettled() {
        assertThatThrownBy(() -> PaymentStateMachine.validate(PENDING, SETTLED))
                .isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void settledIsTerminal() {
        for (var target : np.gov.digital.platformbenefits.enums.PaymentStatus.values()) {
            assertThat(PaymentStateMachine.isAllowed(SETTLED, target)).isFalse();
        }
    }

    @Test
    void failedIsTerminal() {
        for (var target : np.gov.digital.platformbenefits.enums.PaymentStatus.values()) {
            assertThat(PaymentStateMachine.isAllowed(FAILED, target)).isFalse();
        }
    }

    @Test
    void cancelledIsTerminal() {
        for (var target : np.gov.digital.platformbenefits.enums.PaymentStatus.values()) {
            assertThat(PaymentStateMachine.isAllowed(CANCELLED, target)).isFalse();
        }
    }
}
