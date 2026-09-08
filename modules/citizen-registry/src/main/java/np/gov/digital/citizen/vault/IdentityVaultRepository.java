package np.gov.digital.citizen.vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IdentityVaultRepository extends JpaRepository<IdentityVault, UUID> {
}
