package np.gov.digital.platformvitalevents.statemachine;

import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException;
import org.junit.jupiter.api.Test;

import static np.gov.digital.platformvitalevents.enums.VitalEventStatus.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the shared vital-event state machine (SDD Extended Modules
 * §4.1): SUBMITTED -> PENDING_APPROVAL -> {APPROVED | REJECTED | CAO_REVIEW},
 * CAO_REVIEW -> {APPROVED | REJECTED}, with APPROVED/REJECTED terminal.
 */
class VitalEventStateMachineTest {

    @Test
    void submittedToPendingApprovalIsAllowed() {
        assertThatNoException().isThrownBy(() ->
                VitalEventStateMachine.validate(SUBMITTED, PENDING_APPROVAL));
    }

    @Test
    void pendingApprovalToApprovedIsAllowed() {
        assertThat(VitalEventStateMachine.isAllowed(PENDING_APPROVAL, APPROVED)).isTrue();
    }

    @Test
    void pendingApprovalToRejectedIsAllowed() {
        assertThat(VitalEventStateMachine.isAllowed(PENDING_APPROVAL, REJECTED)).isTrue();
    }

    @Test
    void pendingApprovalToCaoReviewIsAllowed() {
        assertThat(VitalEventStateMachine.isAllowed(PENDING_APPROVAL, CAO_REVIEW)).isTrue();
    }

    @Test
    void caoReviewToApprovedIsAllowed() {
        assertThat(VitalEventStateMachine.isAllowed(CAO_REVIEW, APPROVED)).isTrue();
    }

    @Test
    void caoReviewToRejectedIsAllowed() {
        assertThat(VitalEventStateMachine.isAllowed(CAO_REVIEW, REJECTED)).isTrue();
    }

    @Test
    void caoReviewCannotGoBackToPendingApproval() {
        assertThat(VitalEventStateMachine.isAllowed(CAO_REVIEW, PENDING_APPROVAL)).isFalse();
    }

    @Test
    void submittedCannotSkipDirectlyToApproved() {
        assertThatThrownBy(() -> VitalEventStateMachine.validate(SUBMITTED, APPROVED))
                .isInstanceOf(InvalidVitalEventTransitionException.class);
    }

    @Test
    void submittedCannotSkipDirectlyToCaoReview() {
        assertThat(VitalEventStateMachine.isAllowed(SUBMITTED, CAO_REVIEW)).isFalse();
    }

    @Test
    void approvedIsTerminal() {
        assertThat(VitalEventStateMachine.isTerminal(APPROVED)).isTrue();
        for (VitalEventStatus target : VitalEventStatus.values()) {
            assertThat(VitalEventStateMachine.isAllowed(APPROVED, target)).isFalse();
        }
    }

    @Test
    void rejectedIsTerminal() {
        assertThat(VitalEventStateMachine.isTerminal(REJECTED)).isTrue();
        for (VitalEventStatus target : VitalEventStatus.values()) {
            assertThat(VitalEventStateMachine.isAllowed(REJECTED, target)).isFalse();
        }
    }

    @Test
    void submittedIsNotTerminal() {
        assertThat(VitalEventStateMachine.isTerminal(SUBMITTED)).isFalse();
    }
}
