package np.gov.digital.platformvitalevents.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectVitalEventRequest {

    @NotBlank(message = "A rejection reason is required")
    private String reason;
}
