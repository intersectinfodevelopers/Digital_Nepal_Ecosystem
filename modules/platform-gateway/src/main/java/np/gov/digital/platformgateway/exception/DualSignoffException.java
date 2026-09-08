package np.gov.digital.platformgateway.exception;

/**
 * LEGAL_MANDATE_NO_CONSENT requires two DISTINCT Central Admins to sign
 * off before a purpose can bypass citizen consent — the same person
 * cannot provide both approvals (chk_no_consent_dual_signoff_distinct,
 * V41).
 */
public class DualSignoffException extends RuntimeException {
    public DualSignoffException(String message) {
        super(message);
    }
}
