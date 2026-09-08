package np.gov.digital.platformgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentInitiateRequest {

    @NotNull(message = "citizenId is required")
    private UUID citizenId;

    @NotBlank(message = "purposeCode is required")
    private String purposeCode;
}
