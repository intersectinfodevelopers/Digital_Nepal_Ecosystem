package np.gov.digital.platformvitalevents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BirthRegistrationRequest {

    @NotNull(message = "Ward ID is required")
    private UUID wardId;

    @NotBlank(message = "Child's Nepali name is required")
    @Size(max = 300)
    private String childNameNp;

    @NotBlank(message = "Child's English name is required")
    @Size(max = 300)
    private String childNameEn;

    @NotBlank(message = "Sex is required")
    @Pattern(regexp = "MALE|FEMALE|OTHER", message = "Sex must be MALE, FEMALE, or OTHER")
    private String sex;

    @NotBlank(message = "Date of birth is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date of birth must be in YYYY-MM-DD format")
    private String dateOfBirth;

    @NotBlank(message = "Place of birth is required")
    @Size(max = 300)
    private String placeOfBirth;

    // At least one of {fatherCitizenId, fatherNameText} is required —
    // enforced in BirthRegistrationService (and, as a last-resort backstop,
    // chk_birth_record_father_identified at the DB level).
    private UUID fatherCitizenId;
    private String fatherNameText;

    // Same rule for the mother.
    private UUID motherCitizenId;
    private String motherNameText;

    private BigDecimal birthWeightKg;

    @Pattern(regexp = "NORMAL|CESAREAN|ASSISTED", message = "Delivery type must be NORMAL, CESAREAN, or ASSISTED")
    private String deliveryType;

    @Size(max = 300)
    private String attendingFacility;
}
