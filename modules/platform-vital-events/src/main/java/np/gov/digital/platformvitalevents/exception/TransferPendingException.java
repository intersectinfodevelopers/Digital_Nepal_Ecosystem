package np.gov.digital.platformvitalevents.exception;

/** ERR_TRANSFER_PENDING (SDD Extended Modules §10). */
public class TransferPendingException extends RuntimeException {
    public TransferPendingException(String message) {
        super(message);
    }
}
