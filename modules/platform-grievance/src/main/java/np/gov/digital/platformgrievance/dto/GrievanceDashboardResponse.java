package np.gov.digital.platformgrievance.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrievanceDashboardResponse {

    private long openCount;
    private long breachedCount;
    private long resolvedCount;
    private UUID municipalityId;
    private Instant generatedAt;
}