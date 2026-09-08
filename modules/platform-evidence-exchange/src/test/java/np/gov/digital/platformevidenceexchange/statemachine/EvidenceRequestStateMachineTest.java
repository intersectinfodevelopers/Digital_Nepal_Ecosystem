package np.gov.digital.platformevidenceexchange.statemachine;

import np.gov.digital.platformevidenceexchange.exception.InvalidEvidenceRequestTransitionException;
import org.junit.jupiter.api.Test;

import static np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus.*;
import static org.assertj.core.api.Assertions.*;

class EvidenceRequestStateMachineTest {

    @Test
    void pendingToRespondedIsAllowed() {
        assertThatNoException().isThrownBy(() -> EvidenceRequestStateMachine.validate(PENDING, RESPONDED));
    }

    @Test
    void pendingToFailedIsAllowed() {
        assertThat(EvidenceRequestStateMachine.isAllowed(PENDING, FAILED)).isTrue();
    }

    @Test
    void pendingToExpiredIsAllowed() {
        assertThat(EvidenceRequestStateMachine.isAllowed(PENDING, EXPIRED)).isTrue();
    }

    @Test
    void respondedIsTerminal() {
        for (var target : np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus.values()) {
            assertThat(EvidenceRequestStateMachine.isAllowed(RESPONDED, target)).isFalse();
        }
    }

    @Test
    void expiredIsTerminal() {
        for (var target : np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus.values()) {
            assertThat(EvidenceRequestStateMachine.isAllowed(EXPIRED, target)).isFalse();
        }
    }

    @Test
    void respondedCannotBeReExpired() {
        assertThatThrownBy(() -> EvidenceRequestStateMachine.validate(RESPONDED, EXPIRED))
                .isInstanceOf(InvalidEvidenceRequestTransitionException.class);
    }
}
