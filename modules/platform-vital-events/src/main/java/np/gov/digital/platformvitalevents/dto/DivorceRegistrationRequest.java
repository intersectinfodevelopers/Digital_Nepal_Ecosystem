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
public class DivorceRegistrationRequest {

    @NotNull(message = "Ward ID is required")
    private UUID wardId;

    @NotNull(message = "First spouse's citizen ID is required")
    private UUID spouse1CitizenId;

    @NotNull(message = "Second spouse's citizen ID is required")
    private UUID spouse2CitizenId;

    @NotBlank(message = "Divorce date is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Divorce date must be in YYYY-MM-DD format")
    private String divorceDate;

    @NotBlank(message = "Court name is required")
    @Size(max = 300)
    private String courtName;

    @NotBlank(message = "Court order number is required (ERR_DIVORCE_NO_COURT_ORDER)")
    @Size(max = 200)
    private String courtOrderNo;
}
