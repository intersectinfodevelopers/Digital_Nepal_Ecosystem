package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformgrievance.dto.GrievanceDashboardResponse;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrievanceDashboardService {

    private final GrievanceRepository grievanceRepository;

    private static final List<GrievanceStatus> CLOSED_STATUSES = List.of(
            GrievanceStatus.CLOSED,
            GrievanceStatus.CLOSED_INVALID
    );

    private static final List<GrievanceStatus> RESOLVED_STATUSES = List.of(
            GrievanceStatus.RESOLVED_WARD,
            GrievanceStatus.RESOLVED_JUDICIAL,
            GrievanceStatus.RESOLVED_BOARD,
            GrievanceStatus.CLOSED
    );

    public GrievanceDashboardResponse getDashboard(UUID municipalityId) {
        long open     = grievanceRepository
                .countOpenByMunicipality(municipalityId, CLOSED_STATUSES);
        long breached = grievanceRepository
                .countBreachedByMunicipality(municipalityId, CLOSED_STATUSES);
        long resolved = grievanceRepository
                .countResolvedByMunicipality(municipalityId, RESOLVED_STATUSES);

        log.info("GrievanceDashboard: municipality={} open={} breached={} resolved={}",
                municipalityId, open, breached, resolved);

        return GrievanceDashboardResponse.builder()
                .municipalityId(municipalityId)
                .openCount(open)
                .breachedCount(breached)
                .resolvedCount(resolved)
                .generatedAt(Instant.now())
                .build();
    }
}