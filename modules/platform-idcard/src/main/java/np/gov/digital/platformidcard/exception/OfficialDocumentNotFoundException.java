package np.gov.digital.platformidcard.exception;

import java.util.UUID;

public class OfficialDocumentNotFoundException extends RuntimeException {
    public OfficialDocumentNotFoundException(UUID id) {
        super("No official document with ID: " + id);
    }
}
