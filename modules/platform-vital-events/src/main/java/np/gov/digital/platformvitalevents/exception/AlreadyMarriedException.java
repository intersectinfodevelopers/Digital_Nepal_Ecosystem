package np.gov.digital.platformvitalevents.exception;

/** ERR_MARRIAGE_ALREADY_MARRIED (bigamy, SDD Extended Modules §10) — hard-blocked, no role can override. */
public class AlreadyMarriedException extends RuntimeException {
    public AlreadyMarriedException(String message) {
        super(message);
    }
}
