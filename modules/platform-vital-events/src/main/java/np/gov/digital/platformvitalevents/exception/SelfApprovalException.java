package np.gov.digital.platformvitalevents.exception;

/**
 * Governance Tiers §3 — no-self-approval — applies here the same way it
 * does to citizen edit requests (auth module's ApprovalService): whoever
 * submitted a vital event can never be the one who approves or rejects
 * it, regardless of role.
 */
public class SelfApprovalException extends RuntimeException {
    public SelfApprovalException(String message) {
        super(message);
    }
}
