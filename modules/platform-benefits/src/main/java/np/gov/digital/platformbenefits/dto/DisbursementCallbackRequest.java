package np.gov.digital.platformbenefits.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementCallbackRequest {

    @NotNull(message = "success is required")
    private Boolean success;

    // Required when success = false, ignored otherwise — validated in
    // the service, not here (cross-field).
    private String failureReason;
}
