package np.gov.digital.platformgateway.repository;

import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelyingPartyRepository extends JpaRepository<RelyingParty, UUID> {

    Optional<RelyingParty> findByClientId(String clientId);

    /** RelyingPartyCertExpiryJob's query — active parties whose cert has lapsed. */
    List<RelyingParty> findByStatusAndCertificateExpiresAtBefore(RelyingPartyStatus status, Instant cutoff);

    /** GET /v1/admin/relying-parties/revoked — public transparency list. */
    List<RelyingParty> findByStatus(RelyingPartyStatus status);
}
