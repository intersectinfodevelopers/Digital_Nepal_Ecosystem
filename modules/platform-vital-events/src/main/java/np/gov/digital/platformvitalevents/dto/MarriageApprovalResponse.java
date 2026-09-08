package np.gov.digital.platformvitalevents.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarriageApprovalResponse {
    private UUID spouse1CitizenId;
    private UUID spouse2CitizenId;
    private UUID relocatedCitizenId;
    private UUID relocatedToWardId;
}
