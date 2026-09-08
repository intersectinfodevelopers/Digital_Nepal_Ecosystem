package np.gov.digital.platformgateway.repository;

import np.gov.digital.platformgateway.entity.GatewayAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GatewayAccessLogRepository extends JpaRepository<GatewayAccessLog, UUID> {

    Page<GatewayAccessLog> findByCitizen_IdOrderByAccessedAtDesc(UUID citizenId, Pageable pageable);
}
