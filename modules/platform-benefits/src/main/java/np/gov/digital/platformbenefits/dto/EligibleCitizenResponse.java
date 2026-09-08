package np.gov.digital.platformbenefits.dto;

import lombok.*;
import np.gov.digital.platformbenefits.enums.BenefitType;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibleCitizenResponse {
    private UUID citizenId;
    private String nameEn;
    private BenefitType benefitType;
    private String eligibilityReason;
}
