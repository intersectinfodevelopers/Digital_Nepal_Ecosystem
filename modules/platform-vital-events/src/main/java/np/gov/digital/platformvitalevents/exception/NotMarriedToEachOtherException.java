package np.gov.digital.platformvitalevents.exception;

/**
 * A divorce can only be registered between two citizens who are
 * currently married to each other (SDD Extended Modules §4.5).
 */
public class NotMarriedToEachOtherException extends RuntimeException {
    public NotMarriedToEachOtherException(String message) {
        super(message);
    }
}
