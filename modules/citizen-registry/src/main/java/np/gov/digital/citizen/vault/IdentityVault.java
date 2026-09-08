package np.gov.digital.citizen.vault;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Isolated store for NID/citizenship ciphertext (Extended Modules §2.3).
 * A citizen row holds only a {@code reference_token} — never the
 * ciphertext directly. See {@link IdentityVaultService}, the only class
 * that should ever read or write this table's ciphertext.
 */
@Entity
@Table(name = "identity_vault")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityVault {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reference_token", updatable = false, nullable = false)
    private UUID referenceToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "vault_type", nullable = false, length = 20)
    private VaultType vaultType;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "TEXT")
    private String ciphertext;

    @Column(name = "encryption_key_version", nullable = false, length = 10)
    private String encryptionKeyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
