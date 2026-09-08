package np.gov.digital.platformbenefits.exception;

/**
 * A disbursement can only be initiated for a citizen EligibilityService
 * currently finds eligible for the given benefit type — re-checked at
 * disbursement time, not just when the eligible-list view was generated,
 * since eligibility can change between the two.
 */
public class IneligibleCitizenException extends RuntimeException {
    public IneligibleCitizenException(String message) {
        super(message);
    }
}
