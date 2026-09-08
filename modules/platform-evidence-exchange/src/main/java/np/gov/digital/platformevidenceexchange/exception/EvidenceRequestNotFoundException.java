package np.gov.digital.platformevidenceexchange.exception;

import java.util.UUID;

public class EvidenceRequestNotFoundException extends RuntimeException {
    public EvidenceRequestNotFoundException(UUID id) {
        super("No evidence request with ID: " + id);
    }
}
