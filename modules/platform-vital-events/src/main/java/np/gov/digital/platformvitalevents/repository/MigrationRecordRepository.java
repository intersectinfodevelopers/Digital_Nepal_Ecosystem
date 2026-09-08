package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.MigrationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MigrationRecordRepository extends JpaRepository<MigrationRecord, UUID> {

    /**
     * ERR_TRANSFER_PENDING check — any migration_record for this citizen
     * whose vital_event is still SUBMITTED or PENDING_APPROVAL (i.e. not
     * yet decided either way). See V37's migration comment for why this
     * can't be a DB-level partial unique index.
     */
    @Query("""
            SELECT mr FROM MigrationRecord mr
            WHERE mr.citizen.id = :citizenId
              AND mr.vitalEvent.status IN (
                  np.gov.digital.platformvitalevents.enums.VitalEventStatus.SUBMITTED,
                  np.gov.digital.platformvitalevents.enums.VitalEventStatus.PENDING_APPROVAL,
                  np.gov.digital.platformvitalevents.enums.VitalEventStatus.CAO_REVIEW
              )
            """)
    List<MigrationRecord> findPendingByCitizenId(@Param("citizenId") UUID citizenId);
}
