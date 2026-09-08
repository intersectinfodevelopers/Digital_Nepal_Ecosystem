package np.gov.digital.platformgateway.exception;

/** ERR_RELYING_PARTY_CONSENT_REQUIRED (Extended Modules §10). */
public class ConsentRequiredException extends RuntimeException {
    public ConsentRequiredException(String message) {
        super(message);
    }
}
