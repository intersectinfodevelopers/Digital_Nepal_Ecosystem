package np.gov.digital.platformgateway.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.platformgateway.enums.ConsentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * OTP-based commercial consent (Extended Modules §6.4) — otpCodeHash is
 * a one-way hash, same discipline as a password; the plaintext OTP is
 * sent to the citizen's phone (via SparrowSmsService) and never stored.
 */
@Entity
@Table(name = "gateway_consent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private Citizen citizen;

    @Column(name = "relying_party_id", nullable = false, updatable = false)
    private UUID relyingPartyId;

    @Column(name = "purpose_code", nullable = false, length = 50, updatable = false)
    private String purposeCode;

    @Column(name = "otp_code_hash", nullable = false, columnDefinition = "TEXT")
    private String otpCodeHash;

    @Column(name = "otp_expires_at", nullable = false, updatable = false)
    private Instant otpExpiresAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private ConsentStatus status = ConsentStatus.PENDING;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
