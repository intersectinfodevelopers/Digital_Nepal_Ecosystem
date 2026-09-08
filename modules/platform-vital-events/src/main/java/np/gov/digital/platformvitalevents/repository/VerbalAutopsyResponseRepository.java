package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.VerbalAutopsyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VerbalAutopsyResponseRepository extends JpaRepository<VerbalAutopsyResponse, UUID> {
}
