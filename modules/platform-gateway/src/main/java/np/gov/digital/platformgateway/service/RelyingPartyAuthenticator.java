package np.gov.digital.platformgateway.service;

import lombok.RequiredArgsConstructor;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.exception.InvalidClientCredentialsException;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Shared clientId/clientSecret check used by every relying-party-facing
 * gateway endpoint (verify, consent initiate, consent confirm) — see
 * GatewayVerificationService's class Javadoc for why this, rather than
 * a full OAuth2 client-credentials bearer-token flow, is the checked
 * boundary here.
 */
@Component
@RequiredArgsConstructor
public class RelyingPartyAuthenticator {

    private final RelyingPartyRepository relyingPartyRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public RelyingParty authenticate(String clientId, String clientSecret) {
        RelyingParty relyingParty = relyingPartyRepository.findByClientId(clientId)
                .orElseThrow(() -> new InvalidClientCredentialsException("Unknown client_id."));
        if (clientSecret == null || !passwordEncoder.matches(clientSecret, relyingParty.getClientSecretHash())) {
            throw new InvalidClientCredentialsException("Invalid client_secret.");
        }
        return relyingParty;
    }
}
