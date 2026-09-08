package np.gov.digital.platformvitalevents.exception;

/** ERR_MARRIAGE_UNDERAGE (SDD Extended Modules §10) — hard-blocked, no role can override. */
public class UnderageMarriageException extends RuntimeException {
    public UnderageMarriageException(String message) {
        super(message);
    }
}
