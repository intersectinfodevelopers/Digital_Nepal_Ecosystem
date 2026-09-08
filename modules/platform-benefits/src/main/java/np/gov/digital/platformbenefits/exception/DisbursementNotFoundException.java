package np.gov.digital.platformbenefits.exception;

import java.util.UUID;

public class DisbursementNotFoundException extends RuntimeException {
    public DisbursementNotFoundException(UUID id) {
        super("No benefit disbursement with ID: " + id);
    }
}
