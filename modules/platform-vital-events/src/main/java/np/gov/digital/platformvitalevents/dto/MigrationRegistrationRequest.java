package np.gov.digital.platformvitalevents.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationRegistrationRequest {

    @NotNull(message = "Citizen ID is required")
    private UUID citizenId;

    // Destination ward. The citizen's current ward is read from their own
    // record and used as the "from" ward — not taken from the request, so
    // it can't be spoofed to something other than where they actually are.
    @NotNull(message = "Destination ward ID is required")
    private UUID toWardId;

    @Size(max = 300)
    private String reason;
}
