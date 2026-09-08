package np.gov.digital.platformevidenceexchange.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceRequestCallbackRequest {

    @NotNull(message = "success is required")
    private Boolean success;

    // Required when success = true, ignored otherwise — validated in the
    // service, not here (cross-field). A small structured fact (e.g.
    // {"citizenship_valid": true}), never a document or full record.
    private Map<String, Object> responsePayload;

    private String failureReason;
}
