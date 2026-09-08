package np.gov.digital.platformgateway.repository;

import np.gov.digital.platformgateway.entity.GatewayConsent;
import np.gov.digital.platformgateway.enums.ConsentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GatewayConsentRepository extends JpaRepository<GatewayConsent, UUID> {

    Optional<GatewayConsent> findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
            UUID citizenId, UUID relyingPartyId, String purposeCode, ConsentStatus status);
}
