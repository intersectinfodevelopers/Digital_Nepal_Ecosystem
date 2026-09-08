package np.gov.digital.platformbenefits.enums;

/**
 * PENDING -&gt; INITIATED -&gt; {SETTLED | FAILED}; PENDING/INITIATED can
 * also move to CANCELLED. See BenefitDisbursementStateMachine.
 */
public enum PaymentStatus {
    PENDING,
    INITIATED,
    SETTLED,
    FAILED,
    CANCELLED
}
