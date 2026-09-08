package np.gov.digital.platformgateway.repository;

import np.gov.digital.platformgateway.entity.CitizenRelyingPartyToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CitizenRelyingPartyTokenRepository extends JpaRepository<CitizenRelyingPartyToken, UUID> {

    Optional<CitizenRelyingPartyToken> findByCitizen_IdAndRelyingPartyId(UUID citizenId, UUID relyingPartyId);
}
