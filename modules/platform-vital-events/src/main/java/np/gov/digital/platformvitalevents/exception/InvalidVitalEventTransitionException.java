package np.gov.digital.platformvitalevents.exception;

import np.gov.digital.platformvitalevents.enums.VitalEventStatus;

public class InvalidVitalEventTransitionException extends RuntimeException {
    public InvalidVitalEventTransitionException(VitalEventStatus from, VitalEventStatus to) {
        super("Cannot transition vital event from " + from + " to " + to);
    }
}
