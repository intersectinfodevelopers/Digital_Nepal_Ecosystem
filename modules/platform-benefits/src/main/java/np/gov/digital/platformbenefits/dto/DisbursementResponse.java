package np.gov.digital.platformbenefits.dto;

import lombok.*;
import np.gov.digital.platformbenefits.enums.BenefitType;
import np.gov.digital.platformbenefits.enums.PaymentRail;
import np.gov.digital.platformbenefits.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementResponse {
    private UUID id;
    private UUID citizenId;
    private BenefitType benefitType;
    private String period;
    private BigDecimal amountNpr;
    private PaymentRail paymentRail;
    private PaymentStatus paymentStatus;
    private String externalReference;
    private String failureReason;
    private Instant initiatedAt;
    private Instant settledAt;
}
