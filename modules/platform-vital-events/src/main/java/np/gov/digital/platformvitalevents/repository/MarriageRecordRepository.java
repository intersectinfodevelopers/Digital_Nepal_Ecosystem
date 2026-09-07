package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.MarriageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MarriageRecordRepository extends JpaRepository<MarriageRecord, UUID> {
}
