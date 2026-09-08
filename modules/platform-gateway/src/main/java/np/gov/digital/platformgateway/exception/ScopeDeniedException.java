package np.gov.digital.platformgateway.exception;

/** ERR_RELYING_PARTY_SCOPE_DENIED (Extended Modules §10). */
public class ScopeDeniedException extends RuntimeException {
    public ScopeDeniedException(String message) {
        super(message);
    }
}
