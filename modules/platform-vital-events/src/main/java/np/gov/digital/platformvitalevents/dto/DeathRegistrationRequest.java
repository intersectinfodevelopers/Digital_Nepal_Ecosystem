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
public class DeathRegistrationRequest {

    @NotNull(message = "Ward ID is required")
    private UUID wardId;

    @NotNull(message = "Citizen ID of the deceased is required")
    private UUID citizenId;

    @NotBlank(message = "Date of death is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date of death must be in YYYY-MM-DD format")
    private String dateOfDeath;

    @NotBlank(message = "Place of death is required")
    @Size(max = 300)
    private String placeOfDeath;

    @Size(max = 300)
    private String immediateCauseOfDeath;

    @Pattern(regexp = "NATURAL|ACCIDENT|SUICIDE|HOMICIDE|UNDETERMINED",
            message = "Manner of death must be NATURAL, ACCIDENT, SUICIDE, HOMICIDE, or UNDETERMINED")
    private String mannerOfDeath;

    @NotBlank(message = "Informant name is required")
    @Size(max = 300)
    private String informantName;

    @NotBlank(message = "Informant's relationship to the deceased is required")
    @Size(max = 100)
    private String informantRelation;

    @Size(max = 300)
    private String certifyingFacility;
}
