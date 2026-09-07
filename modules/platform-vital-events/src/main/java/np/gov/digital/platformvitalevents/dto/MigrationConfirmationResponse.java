package np.gov.digital.platformvitalevents.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationConfirmationResponse {
    private UUID vitalEventId;
    private boolean losingAdminConfirmed;
    private boolean receivingAdminConfirmed;
    // True only once BOTH sides have confirmed and the ward transfer has
    // actually been applied to the citizen record.
    private boolean transferCompleted;
}
