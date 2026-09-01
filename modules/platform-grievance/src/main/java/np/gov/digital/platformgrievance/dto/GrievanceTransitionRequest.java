package np.gov.digital.platformgrievance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrievanceTransitionRequest {

    @NotNull(message = "targetStatus is required")
    private GrievanceStatus targetStatus;

    // Required when moving to RESOLVED_WARD or CLOSED_INVALID
    private String note;

    private String citizenMobile;
}