package np.gov.digital.platformvitalevents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerbalAutopsyRequest {

    @NotBlank(message = "Respondent name is required")
    private String respondentName;

    @NotBlank(message = "Respondent's relationship to the deceased is required")
    private String respondentRelation;

    @NotBlank(message = "Interview date is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Interview date must be in YYYY-MM-DD format")
    private String interviewDate;

    // Raw WHO 2016 VA instrument answers — question-id -> answer. Not
    // validated field-by-field here; the instrument itself is versioned
    // and evolves independently of this API.
    @NotNull(message = "Responses are required")
    @NotEmpty(message = "Responses must not be empty")
    private Map<String, Object> responses;

    private String probableCauseOfDeath;
}
