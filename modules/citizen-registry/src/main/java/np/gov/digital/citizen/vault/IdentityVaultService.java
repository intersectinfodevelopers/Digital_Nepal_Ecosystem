package np.gov.digital.citizen.vault;

import lombok.RequiredArgsConstructor;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The only class in this codebase that should ever read or write
 * NID/citizenship ciphertext. Everything else — {@code CitizenService}
 * included — deals exclusively in reference tokens.
 *
 * True DB-role isolation (a separate Postgres role with INSERT-only grant
 * on {@code identity_vault}, no SELECT, for the ordinary application
 * connection — Extended Modules §2.3) needs a second datasource/credential
 * and is tracked as infrastructure follow-up, not something a single JPA
 * service class can enforce on its own. What this class gives you today is
 * the logical boundary: nothing outside this package touches ciphertext.
 */
@Service
@RequiredArgsConstructor
public class IdentityVaultService {

    // Bumped whenever app.encryption.key is rotated. A real rotation
    // workflow (Governance Tiers §5 "pepper rotation" two-person action)
    // would re-encrypt existing rows under the new version and update this
    // per-row — out of scope here; this just records which key encrypted
    // each row so a future rotation job knows what needs re-wrapping.
    private static final String CURRENT_KEY_VERSION = "v1";

    private final IdentityVaultRepository identityVaultRepository;
    private final NidEncryptionUtil nidEncryptionUtil;

    @Transactional
    public UUID store(VaultType type, String plaintext) {
        IdentityVault vault = IdentityVault.builder()
                .vaultType(type)
                .ciphertext(nidEncryptionUtil.encrypt(plaintext))
                .encryptionKeyVersion(CURRENT_KEY_VERSION)
                .build();
        return identityVaultRepository.save(vault).getReferenceToken();
    }

    @Transactional(readOnly = true)
    public String retrieve(UUID referenceToken) {
        IdentityVault vault = identityVaultRepository.findById(referenceToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Identity vault reference not found: " + referenceToken));
        return nidEncryptionUtil.decrypt(vault.getCiphertext());
    }
}
