package np.gov.digital.platformvitalevents.exception;

/**
 * The two-party migration handoff (SDD Extended Modules §4.6) requires
 * the losing confirmation to come from a LOCAL_BODY_ADMIN of the losing
 * municipality, and the receiving confirmation from a LOCAL_BODY_ADMIN of
 * the receiving municipality — never the other way round, and never
 * either side confirmed by an admin of an unrelated municipality.
 */
public class WrongMunicipalityException extends RuntimeException {
    public WrongMunicipalityException(String message) {
        super(message);
    }
}
