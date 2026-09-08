package np.gov.digital.platformgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import np.gov.digital.platformgateway.enums.OrganizationType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRelyingPartyRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 300)
    private String name;

    @NotNull(message = "Organization type is required")
    private OrganizationType organizationType;
}
