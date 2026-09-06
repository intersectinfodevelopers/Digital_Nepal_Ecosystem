package np.gov.digital.citizen.exception;

import java.util.UUID;

public class CitizenNotFoundException extends RuntimeException {
    public CitizenNotFoundException(UUID citizenId) {
        super("No active citizen found with ID: " + citizenId);
    }
}
