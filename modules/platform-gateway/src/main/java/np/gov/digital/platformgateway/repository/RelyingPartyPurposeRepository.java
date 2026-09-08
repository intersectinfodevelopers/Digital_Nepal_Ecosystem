package np.gov.digital.platformgateway.repository;

import np.gov.digital.platformgateway.entity.RelyingPartyPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RelyingPartyPurposeRepository extends JpaRepository<RelyingPartyPurpose, UUID> {

    Optional<RelyingPartyPurpose> findByRelyingParty_IdAndPurposeCode(UUID relyingPartyId, String purposeCode);
}
