package np.gov.digital.platformvitalevents.dto;

import lombok.*;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarriageRegistrationResponse {
    private UUID vitalEventId;
    private VitalEventStatus status;
    private UUID spouse1CitizenId;
    private UUID spouse2CitizenId;
    private UUID wardId;
    private Instant submittedAt;
    private String message;
}
