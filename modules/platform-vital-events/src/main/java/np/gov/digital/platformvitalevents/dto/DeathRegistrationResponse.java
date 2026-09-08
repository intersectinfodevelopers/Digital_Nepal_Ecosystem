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
public class DeathRegistrationResponse {
    private UUID vitalEventId;
    private VitalEventStatus status;
    private UUID citizenId;
    private UUID wardId;
    private Instant submittedAt;
    private String message;
}
