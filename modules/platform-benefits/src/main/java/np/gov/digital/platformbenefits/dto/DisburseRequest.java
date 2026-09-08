package np.gov.digital.platformbenefits.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.*;
import np.gov.digital.platformbenefits.enums.BenefitType;
import np.gov.digital.platformbenefits.enums.PaymentRail;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisburseRequest {

    @NotNull(message = "Benefit type is required")
    private BenefitType benefitType;

    @NotNull(message = "Period is required")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Period must be in YYYY-MM format")
    private String period;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amountNpr;

    @NotNull(message = "Payment rail is required")
    private PaymentRail paymentRail;
}
