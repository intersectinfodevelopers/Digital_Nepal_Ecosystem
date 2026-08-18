package np.gov.digital.platformgrievance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrievanceRejectionRequest {

    @NotBlank(message = "rejection reason is required")
    private String rejectionReason;
}