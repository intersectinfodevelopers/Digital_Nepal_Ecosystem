package np.gov.digital.platformgrievance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrievanceEscalationRequest {

    @NotNull(message = "municipalityId is required")
    private UUID municipalityId;

    @NotBlank(message = "escalation reason is required")
    private String reason;
}