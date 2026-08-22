package np.gov.digital.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import np.gov.digital.auth.config.JwtProperties;
import np.gov.digital.auth.entity.User;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * SECURITY FIX: this service previously signed and verified JWTs with
 * Keys.hmacShaKeyFor(...) — HS256, a SYMMETRIC algorithm. That means the
 * exact same secret both mints tokens and verifies them. Any service,
 * module, or log line that ever needed to verify a token also had
 * everything required to forge an arbitrary admin token for any tier.
 *
 * The SDD explicitly requires RS256 (asymmetric) for this reason (Section
 * 3.1, Appendix B glossary: "Preferred over HS256 (symmetric) because the
 * public key can be distributed to read services without exposing signing
 * capability"). This version signs with an RSA private key and verifies
 * with the matching public key — the two are not interchangeable, so a
 * component that only holds the public key (e.g. a future read-only
 * analytics service for Province/Central dashboards) can validate tokens
 * but can never mint one.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void init() {
        if (jwtProperties.getPrivateKeyPem() == null || jwtProperties.getPrivateKeyPem().isBlank()) {
            throw new IllegalStateException(
                    "jwt.private-key-pem is not set. Refusing to start without an RS256 "
                    + "signing key rather than fall back to a weaker scheme. See SDD Section 3.1.1 "
                    + "for key storage requirements (Vault/AWS Secrets Manager in prod).");
        }
        if (jwtProperties.getPublicKeyPem() == null || jwtProperties.getPublicKeyPem().isBlank()) {
            throw new IllegalStateException(
                    "jwt.public-key-pem is not set. Refusing to start without an RS256 "
                    + "verification key.");
        }
        try {
            this.privateKey = loadPrivateKey(resolvePemContent(jwtProperties.getPrivateKeyPem()));
            this.publicKey  = loadPublicKey(resolvePemContent(jwtProperties.getPublicKeyPem()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RS256 JWT key pair", e);
        }
    }

    // Accepts either raw PEM text (as injected by Vault/a Docker secret) or
    // "file:/path/to/key.pem" for local dev.
    private String resolvePemContent(String configuredValue) throws IOException {
        if (configuredValue.startsWith("file:")) {
            Path path = Path.of(configuredValue.substring("file:".length()));
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        return configuredValue;
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("user_id", user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("ward_id", user.getWardId())
                .claim("municipality_id", user.getMunicipalityId())
                .claim("province_id", user.getProvinceId())
                .issuedAt(new Date())
                .expiration(new Date(
                        System.currentTimeMillis()
                                + jwtProperties.getAccessTokenExpiration()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(
                        System.currentTimeMillis()
                                + jwtProperties.getRefreshTokenExpiration()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, User user) {
        return extractUsername(token).equals(user.getEmail())
                && !isTokenExpired(token);
    }

    public <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver) {

        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        // verifyWith(PublicKey) — verification only ever needs the public
        // half of the key pair, never the private key.
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
