package np.gov.digital.platformvitalevents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarriageRegistrationRequest {

    @NotNull(message = "Ward ID is required")
    private UUID wardId;

    @NotNull(message = "First spouse's citizen ID is required")
    private UUID spouse1CitizenId;

    @NotNull(message = "Second spouse's citizen ID is required")
    private UUID spouse2CitizenId;

    @NotBlank(message = "Marriage date is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Marriage date must be in YYYY-MM-DD format")
    private String marriageDate;

    @NotBlank(message = "Marriage place is required")
    @Size(max = 300)
    private String marriagePlace;

    @Size(max = 300)
    private String witness1Name;

    @Size(max = 300)
    private String witness2Name;

    // Must equal spouse1CitizenId or spouse2CitizenId if set — validated in
    // MarriageRegistrationService, not here (needs both IDs to compare).
    // Null means neither spouse relocates.
    private UUID relocatingCitizenId;
}
