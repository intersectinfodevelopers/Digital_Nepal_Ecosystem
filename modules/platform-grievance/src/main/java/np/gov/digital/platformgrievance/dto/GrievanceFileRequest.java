package np.gov.digital.platformgrievance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import np.gov.digital.platformgrievance.enums.GrievanceCategory;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrievanceFileRequest {

    @NotNull(message = "citizenId is required")
    private UUID citizenId;

    @NotNull(message = "category is required")
    private GrievanceCategory category;

    @NotBlank(message = "description is required")
    @Size(max = 4000)
    private String description;

    private List<String> attachmentUrls;

    private String citizenMobile;
}