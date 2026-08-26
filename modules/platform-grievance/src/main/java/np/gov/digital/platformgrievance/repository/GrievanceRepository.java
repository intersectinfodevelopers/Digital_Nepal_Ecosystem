package np.gov.digital.platformgrievance.repository;

import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrievanceRepository extends JpaRepository<Grievance, UUID> {

    boolean existsByTrackingCode(String trackingCode);

    Optional<Grievance> findByTrackingCode(String trackingCode);

    @Query("SELECT g FROM Grievance g WHERE g.slaDueAt < :now " +
            "AND g.slaBreached = false " +
            "AND g.status NOT IN :closedStatuses")
    List<Grievance> findUnflaggedBreachedGrievances(
            @Param("now") Instant now,
            @Param("closedStatuses") List<GrievanceStatus> closedStatuses
    );

    @Modifying
    @Query("UPDATE Grievance g SET g.slaBreached = true, g.updatedAt = :now " +
            "WHERE g.id IN :ids")
    void markSlaBreached(@Param("ids") List<UUID> ids, @Param("now") Instant now);


    @Query("SELECT COUNT(g) FROM Grievance g WHERE g.municipalityId = :municipalityId " +
            "AND g.status NOT IN :closedStatuses")
    long countOpenByMunicipality(
            @Param("municipalityId") UUID municipalityId,
            @Param("closedStatuses") List<GrievanceStatus> closedStatuses
    );

    @Query("SELECT COUNT(g) FROM Grievance g WHERE g.municipalityId = :municipalityId " +
            "AND g.slaBreached = true " +
            "AND g.status NOT IN :closedStatuses")
    long countBreachedByMunicipality(
            @Param("municipalityId") UUID municipalityId,
            @Param("closedStatuses") List<GrievanceStatus> closedStatuses
    );

    @Query("SELECT COUNT(g) FROM Grievance g WHERE g.municipalityId = :municipalityId " +
            "AND g.status IN :resolvedStatuses")
    long countResolvedByMunicipality(
            @Param("municipalityId") UUID municipalityId,
            @Param("resolvedStatuses") List<GrievanceStatus> resolvedStatuses
    );
}