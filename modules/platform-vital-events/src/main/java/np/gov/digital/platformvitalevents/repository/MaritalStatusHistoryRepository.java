package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.MaritalStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaritalStatusHistoryRepository extends JpaRepository<MaritalStatusHistory, UUID> {

    List<MaritalStatusHistory> findByCitizenIdOrderByEffectiveDateDesc(UUID citizenId);
}
