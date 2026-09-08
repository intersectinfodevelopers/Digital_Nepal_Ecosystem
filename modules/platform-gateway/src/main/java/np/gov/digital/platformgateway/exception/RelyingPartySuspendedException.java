package np.gov.digital.platformgateway.exception;

public class RelyingPartySuspendedException extends RuntimeException {
    public RelyingPartySuspendedException(String message) {
        super(message);
    }
}
