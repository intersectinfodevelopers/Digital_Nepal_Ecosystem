package np.gov.digital.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SECURITY FIX: previously held a single symmetric `secret` string used for
 * both signing and verifying JWTs (HS256). The SDD requires RS256
 * (asymmetric) specifically so that verifying a token never requires
 * possessing the ability to mint one — see JwtService for the key-loading
 * logic and Section 3.1 of the System Design Document.
 *
 * privateKeyPem / publicKeyPem accept either:
 *   - the raw PEM content directly (as injected by Vault/AWS Secrets
 *     Manager/a Docker secret in staging & prod), or
 *   - "file:/path/to/key.pem" for local dev, matching the SDD's Section
 *     3.1.1 dev convention of a keys/private.pem file excluded from Git.
 *
 * There are no defaults for either key. A missing key must fail
 * application startup (see JwtService), not silently fall back to
 * something predictable.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String privateKeyPem;

    private String publicKeyPem;

    private long accessTokenExpiration;

    private long refreshTokenExpiration;

}
