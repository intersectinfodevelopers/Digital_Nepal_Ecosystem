package np.gov.digital.platformevidenceexchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import np.gov.digital.platformevidenceexchange.enums.EvidenceAgency;
import np.gov.digital.platformevidenceexchange.enums.EvidencePurpose;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEvidenceRequestRequest {

    @NotNull(message = "Citizen ID is required")
    private UUID citizenId;

    @NotNull(message = "Target agency is required")
    private EvidenceAgency targetAgency;

    @NotNull(message = "Purpose is required")
    private EvidencePurpose purpose;

    @NotBlank(message = "factRequested is required")
    @Size(max = 300)
    private String factRequested;
}
