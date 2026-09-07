package np.gov.digital.platformvitalevents.exception;

/** ERR_DEATH_ALREADY_RECORDED (SDD Extended Modules §10). */
public class DuplicateDeathRecordException extends RuntimeException {
    public DuplicateDeathRecordException(String message) {
        super(message);
    }
}
