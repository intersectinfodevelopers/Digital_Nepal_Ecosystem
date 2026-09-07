package np.gov.digital.auth.exception;

/**
 * A ward/local-body admin tried to approve an edit request they submitted
 * themselves. Backed by the {@code no_self_approval} CHECK constraint on
 * {@code citizen_edit_requests} (V17) — this exception is the friendly
 * front door to that DB-level guarantee, not a replacement for it.
 */
public class SelfApprovalException extends RuntimeException {
    public SelfApprovalException(String message) {
        super(message);
    }
}
