package np.gov.digital.platformevidenceexchange.repository;

import np.gov.digital.platformevidenceexchange.entity.EvidenceRequest;
import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EvidenceRequestRepository extends JpaRepository<EvidenceRequest, UUID> {

    /** Audit list — GET /v1/evidence-exchange/requests. */
    Page<EvidenceRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<EvidenceRequest> findByCitizen_Id(UUID citizenId, Pageable pageable);

    /** EvidenceRequestExpiryJob's query. */
    List<EvidenceRequest> findByStatusAndExpiresAtBefore(EvidenceRequestStatus status, Instant cutoff);
}
