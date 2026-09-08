package np.gov.digital.platformgateway.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.platformgateway.enums.OrganizationType;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A licensed external organization allowed to verify facts about
 * citizens through the gateway (Extended Modules §6.1). See V41's
 * migration comment for what's genuinely enforced here versus what
 * needs real mTLS infrastructure that doesn't exist yet.
 */
@Entity
@Table(name = "relying_party")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelyingParty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 20, updatable = false)
    private OrganizationType organizationType;

    @Column(name = "client_id", nullable = false, unique = true, updatable = false, length = 100)
    private String clientId;

    // bcrypt hash — the plaintext secret is shown to the licensing
    // Central Admin exactly once, at creation, and never stored again.
    // @JsonIgnore is defense-in-depth: found live, this entity's own
    // relyingParty relation (EAGER on RelyingPartyPurpose) was being
    // serialized whole by a controller returning the JPA entity directly,
    // which put this hash straight into an API response body — never a
    // usable plaintext secret, but still a value that should never leave
    // this service, same discipline as any other password/secret hash.
    @JsonIgnore
    @Column(name = "client_secret_hash", nullable = false, columnDefinition = "TEXT")
    private String clientSecretHash;

    @Column(name = "client_certificate_fingerprint", length = 200)
    private String clientCertificateFingerprint;

    @Column(name = "certificate_expires_at")
    private Instant certificateExpiresAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private RelyingPartyStatus status = RelyingPartyStatus.ACTIVE;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    @Builder.Default
    @Column(name = "consecutive_denied_count", nullable = false)
    private Integer consecutiveDeniedCount = 0;

    @Column(name = "licensed_by", nullable = false, updatable = false)
    private UUID licensedBy;

    @Column(name = "licensed_at", nullable = false, updatable = false)
    private Instant licensedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.licensedAt == null) this.licensedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
