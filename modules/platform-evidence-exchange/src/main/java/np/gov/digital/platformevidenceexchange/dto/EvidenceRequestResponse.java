package np.gov.digital.platformevidenceexchange.dto;

import lombok.*;
import np.gov.digital.platformevidenceexchange.enums.EvidenceAgency;
import np.gov.digital.platformevidenceexchange.enums.EvidencePurpose;
import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceRequestResponse {
    private UUID id;
    private UUID citizenId;
    private EvidenceAgency targetAgency;
    private EvidencePurpose purpose;
    private String factRequested;
    private EvidenceRequestStatus status;
    private String responsePayload;
    private String failureReason;
    private Instant requestedAt;
    private Instant respondedAt;
    private Instant expiresAt;
}
