package np.gov.digital.platformgateway.dto;

import lombok.*;
import np.gov.digital.platformgateway.enums.AccessOutcome;
import np.gov.digital.platformgateway.enums.ConsentMethod;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessLogResponse {
    private UUID relyingPartyId;
    private String relyingPartyName;
    private String purposeCode;
    private String fieldsDisclosed;
    private ConsentMethod consentMethod;
    private AccessOutcome outcome;
    private Instant accessedAt;
}
