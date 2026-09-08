package np.gov.digital.platformbenefits.exception;

import np.gov.digital.platformbenefits.enums.PaymentStatus;

public class InvalidPaymentTransitionException extends RuntimeException {
    public InvalidPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Cannot transition disbursement from " + from + " to " + to);
    }
}
