package np.gov.digital.platformsync.repository;



import np.gov.digital.platformsync.entity.SyncRecord;
import np.gov.digital.platformsync.enums.SyncRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SyncRecordRepository extends JpaRepository<SyncRecord, UUID> {

    List<SyncRecord> findByStatus(SyncRecordStatus status);
    List<SyncRecord> findByBatchId(UUID batchId);
}
