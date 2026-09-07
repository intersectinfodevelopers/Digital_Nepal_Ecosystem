package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.DeathRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeathRecordRepository extends JpaRepository<DeathRecord, UUID> {

    /**
     * ERR_DEATH_ALREADY_RECORDED check — any death_record for this citizen
     * whose vital_event hasn't been rejected. See V34's migration comment
     * for why this can't be a DB-level partial unique index.
     */
    @Query("""
            SELECT dr FROM DeathRecord dr
            WHERE dr.citizen.id = :citizenId
              AND dr.vitalEvent.status <> np.gov.digital.platformvitalevents.enums.VitalEventStatus.REJECTED
            """)
    List<DeathRecord> findActiveByCitizenId(@Param("citizenId") UUID citizenId);
}
