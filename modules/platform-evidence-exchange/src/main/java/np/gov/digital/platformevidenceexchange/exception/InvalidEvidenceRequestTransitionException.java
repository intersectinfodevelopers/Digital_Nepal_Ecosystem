package np.gov.digital.platformevidenceexchange.exception;

import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;

public class InvalidEvidenceRequestTransitionException extends RuntimeException {
    public InvalidEvidenceRequestTransitionException(EvidenceRequestStatus from, EvidenceRequestStatus to) {
        super("Cannot transition evidence request from " + from + " to " + to);
    }
}
