package np.gov.digital.platformgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddPurposeRequest {

    @NotBlank(message = "purposeCode is required")
    private String purposeCode;

    // Field names from CitizenProfileResponse's own shape (e.g. "nameEn",
    // "dob", "wardId") — the whitelist a relying party licensed for this
    // purpose is scoped to. Validated against the real allowed field set
    // in the service, not here.
    @NotNull
    @NotEmpty(message = "allowedFields must not be empty")
    private List<String> allowedFields;
}
