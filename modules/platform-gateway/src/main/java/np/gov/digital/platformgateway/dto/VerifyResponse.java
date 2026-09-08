package np.gov.digital.platformgateway.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyResponse {
    // This relying party's own pairwise reference for this citizen —
    // never the citizen's real ID.
    private UUID pairwiseToken;
    private String purposeCode;
    private Map<String, Object> fields;
}
