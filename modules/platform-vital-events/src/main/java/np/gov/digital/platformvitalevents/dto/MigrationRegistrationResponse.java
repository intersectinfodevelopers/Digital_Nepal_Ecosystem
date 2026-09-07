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
public class MigrationRegistrationResponse {
    private UUID vitalEventId;
    private VitalEventStatus status;
    private UUID citizenId;
    private UUID fromWardId;
    private UUID toWardId;
    private Instant submittedAt;
    private String message;
}
