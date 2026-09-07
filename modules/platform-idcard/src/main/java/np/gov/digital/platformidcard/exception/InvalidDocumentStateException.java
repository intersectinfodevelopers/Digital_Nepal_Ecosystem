package np.gov.digital.platformidcard.exception;

import np.gov.digital.platformidcard.enums.DocumentStatus;

public class InvalidDocumentStateException extends RuntimeException {
    public InvalidDocumentStateException(DocumentStatus actual, String action) {
        super("Cannot " + action + " a document in state " + actual);
    }
}
