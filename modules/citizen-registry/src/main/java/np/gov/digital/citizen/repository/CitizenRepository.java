package np.gov.digital.citizen.repository;

import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitizenRepository extends JpaRepository<Citizen, UUID> {

    // DEPRECATED — see Citizen.nidHash. Use the *NidHmac* methods below for
    // all new duplicate-detection logic.
    @Deprecated
    Optional<Citizen> findByNidHashAndIsActiveTrue(String nidHash);

    @Deprecated
    boolean existsByNidHashAndIsActiveTrue(String nidHash);

    Optional<Citizen> findByNidHmacAndIsActiveTrue(String nidHmac);

    boolean existsByNidHmacAndIsActiveTrue(String nidHmac);

    Optional<Citizen> findByCitizenshipNoNormAndIsActiveTrue(String citizenshipNoNorm);

    boolean existsByCitizenshipHmacAndIsActiveTrue(String citizenshipHmac);

    Page<Citizen> findByWardIdAndIsActiveTrue(UUID wardId, Pageable pageable);

    @Query("SELECT c FROM Citizen c WHERE c.ward.id = :wardId AND c.syncStatus = :status AND c.isActive = true")
    Page<Citizen> findByWardIdAndSyncStatus(
            @Param("wardId") UUID wardId,
            @Param("status") SyncStatus status,
            Pageable pageable
    );


    Optional<Citizen> findByLocalRecordId(UUID localRecordId);

    @Query("""
       SELECT c.versionNumber
       FROM Citizen c
       WHERE c.localRecordId = :localRecordId
       """)
    Optional<Integer> findVersionByLocalRecordId(
            @Param("localRecordId") UUID localRecordId);



    @Query("SELECT c FROM Citizen c WHERE c.isAsyncVerified = false AND c.isActive = true AND c.ward.id = :wardId")
    Page<Citizen> findPendingNidVerification(@Param("wardId") UUID wardId, Pageable pageable);

    @Query("SELECT c.versionNumber FROM Citizen c WHERE c.id = :id")
    Optional<Integer> findVersionById(@Param("id") UUID id);
}