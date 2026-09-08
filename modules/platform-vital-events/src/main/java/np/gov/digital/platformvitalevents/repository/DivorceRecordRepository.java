package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.DivorceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DivorceRecordRepository extends JpaRepository<DivorceRecord, UUID> {
}
