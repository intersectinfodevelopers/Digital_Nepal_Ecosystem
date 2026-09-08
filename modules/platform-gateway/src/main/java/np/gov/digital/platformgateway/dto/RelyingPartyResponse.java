package np.gov.digital.platformgateway.dto;

import lombok.*;
import np.gov.digital.platformgateway.enums.OrganizationType;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelyingPartyResponse {
    private UUID id;
    private String name;
    private OrganizationType organizationType;
    private String clientId;
    private RelyingPartyStatus status;
    private String suspensionReason;
    private Instant certificateExpiresAt;
    private Integer consecutiveDeniedCount;
    private Instant licensedAt;
}
