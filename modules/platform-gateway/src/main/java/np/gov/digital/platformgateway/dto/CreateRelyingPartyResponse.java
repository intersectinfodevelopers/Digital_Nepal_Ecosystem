package np.gov.digital.platformgateway.dto;

import lombok.*;
import np.gov.digital.platformgateway.enums.OrganizationType;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;

import java.util.UUID;

/**
 * The ONLY response that ever carries the plaintext client secret — like
 * a password, it's shown exactly once, at creation, and never stored or
 * retrievable again (relyingParty.clientSecretHash is one-way).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRelyingPartyResponse {
    private UUID id;
    private String name;
    private OrganizationType organizationType;
    private String clientId;
    private String clientSecret;
    private RelyingPartyStatus status;
}
