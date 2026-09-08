package np.gov.digital.platformvitalevents.repository;

import np.gov.digital.platformvitalevents.entity.BirthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BirthRecordRepository extends JpaRepository<BirthRecord, UUID> {

    /**
     * Fuzzy duplicate check for a newborn (§4.2): no NID/citizenship hash
     * exists yet to dedup against, so this flags any existing birth_record
     * in the same ward with a similar English name and the same date of
     * birth. Not a hard block like DuplicateNidException — a human (Ward/
     * Local Body Admin) reviews the flag and decides, since two different
     * newborns with the same name and birthday in the same ward is a real,
     * if uncommon, possibility.
     */
    @Query(value = """
            SELECT br.* FROM birth_record br
            JOIN vital_event ve ON ve.id = br.vital_event_id
            WHERE ve.ward_id = :wardId
              AND br.date_of_birth = :dateOfBirth
              AND similarity(br.child_name_en, :childNameEn) > 0.4
              AND ve.status <> 'REJECTED'
            ORDER BY similarity(br.child_name_en, :childNameEn) DESC
            """, nativeQuery = true)
    List<BirthRecord> findPossibleDuplicates(
            @Param("wardId") UUID wardId,
            @Param("childNameEn") String childNameEn,
            @Param("dateOfBirth") LocalDate dateOfBirth
    );
}
