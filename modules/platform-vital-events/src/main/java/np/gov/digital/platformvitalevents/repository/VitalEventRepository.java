package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VitalEventRepository extends JpaRepository<VitalEvent, UUID> {

    Page<VitalEvent> findByWardId(UUID wardId, Pageable pageable);

    Page<VitalEvent> findByWardIdAndStatus(UUID wardId, VitalEventStatus status, Pageable pageable);

    // Used by the auto-escalation job (Governance Tiers §6): anything still
    // PENDING_APPROVAL after 5 business days moves to CAO_REVIEW
    // automatically, regardless of ward/municipality.
    List<VitalEvent> findByStatusAndSubmittedAtBefore(VitalEventStatus status, Instant cutoff);
}
