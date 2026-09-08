package np.gov.digital.platformvitalevents.exception;

import java.util.UUID;

public class VitalEventNotFoundException extends RuntimeException {
    public VitalEventNotFoundException(UUID id) {
        super("No vital event with ID: " + id);
    }
}
