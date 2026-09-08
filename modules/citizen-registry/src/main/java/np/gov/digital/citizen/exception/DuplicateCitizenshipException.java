package np.gov.digital.citizen.exception;

/** ERR_DUPLICATE_CITIZENSHIP — citizenship_hmac already registered to an active citizen. */
public class DuplicateCitizenshipException extends RuntimeException {
    public DuplicateCitizenshipException(String message) {
        super(message);
    }
}
