package np.gov.digital.platformgateway.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.platformgateway.enums.PurposeStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Purpose-scoped field whitelist (Extended Modules §6.2) — a relying
 * party licensed for this purpose may only ever receive exactly
 * allowedFields for a citizen, never their full record.
 *
 * requiresConsent starts true always; setting it false is the narrow
 * LEGAL_MANDATE_NO_CONSENT path and requires two distinct Central
 * Admins' sign-off (chk_no_consent_dual_signoff_distinct, V41) — see
 * RelyingPartyPurposeService.approveNoConsentException().
 */
@Entity
@Table(name = "relying_party_purpose")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelyingPartyPurpose {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "relying_party_id", nullable = false, updatable = false)
    private RelyingParty relyingParty;

    @Column(name = "purpose_code", nullable = false, length = 50, updatable = false)
    private String purposeCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_fields", nullable = false, columnDefinition = "jsonb")
    private String allowedFields;

    @Builder.Default
    @Column(name = "requires_consent", nullable = false)
    private Boolean requiresConsent = true;

    @Column(name = "no_consent_approved_by_1")
    private UUID noConsentApprovedBy1;

    @Column(name = "no_consent_approved_by_2")
    private UUID noConsentApprovedBy2;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private PurposeStatus status = PurposeStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
