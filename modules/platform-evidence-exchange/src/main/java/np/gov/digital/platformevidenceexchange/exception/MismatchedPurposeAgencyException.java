package np.gov.digital.platformevidenceexchange.exception;

/**
 * CITIZENSHIP_VERIFICATION only makes sense against DAO, NID_VERIFICATION
 * only against NIDMC, VOTER_ROLL_CHECK only against the Election
 * Commission — requesting a citizenship fact from the Election
 * Commission, for instance, is not a request anyone could answer.
 */
public class MismatchedPurposeAgencyException extends RuntimeException {
    public MismatchedPurposeAgencyException(String message) {
        super(message);
    }
}
