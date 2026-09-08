package np.gov.digital.platformgateway.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Never the raw RelyingPartyPurpose entity — that carries an EAGER
 * relyingParty relation, and returning the entity directly once put its
 * clientSecretHash straight into this response body (found live, fixed
 * by introducing this DTO — see RelyingParty.clientSecretHash's own
 * @JsonIgnore for the defense-in-depth half of the fix).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurposeResponse {
    private UUID id;
    private UUID relyingPartyId;
    private String purposeCode;
    private String allowedFields;
    private Boolean requiresConsent;
    private UUID noConsentApprovedBy1;
    private UUID noConsentApprovedBy2;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
