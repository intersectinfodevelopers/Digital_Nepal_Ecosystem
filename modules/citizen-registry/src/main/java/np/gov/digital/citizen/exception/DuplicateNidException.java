package np.gov.digital.citizen.exception;

public class DuplicateNidException extends RuntimeException {

    public DuplicateNidException(String message) {
        super(message);
    }

    public DuplicateNidException() {
        super("An active citizen already exists with this NID");
    }
}