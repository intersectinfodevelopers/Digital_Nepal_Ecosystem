package np.gov.digital.platformgateway.exception;

import java.util.UUID;

public class RelyingPartyNotFoundException extends RuntimeException {
    public RelyingPartyNotFoundException(UUID id) {
        super("No relying party with ID: " + id);
    }
}
