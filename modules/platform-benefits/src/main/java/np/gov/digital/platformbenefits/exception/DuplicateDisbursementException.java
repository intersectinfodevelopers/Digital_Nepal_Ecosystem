package np.gov.digital.platformbenefits.exception;

/** ERR_DUPLICATE_DISBURSEMENT — mirrors uq_benefit_disbursement_active_period (V39). */
public class DuplicateDisbursementException extends RuntimeException {
    public DuplicateDisbursementException(String message) {
        super(message);
    }
}
