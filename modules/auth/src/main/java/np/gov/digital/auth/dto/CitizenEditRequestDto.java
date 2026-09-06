package np.gov.digital.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CitizenEditRequestDto {

    @NotNull
    private UUID citizenId;

    /**
     * Field → new value, restricted at apply-time to the whitelist in
     * ApprovalService (name/contact/demographic fields only — NID,
     * citizenship number, DOB, sex, and ward are excluded and need a
     * separate, more guarded re-verification flow, not a same free-text
     * edit request). Was previously an opaque String; a Map lets
     * ApprovalService.approve() actually apply the change instead of just
     * storing it unread.
     */
    @NotEmpty(message = "At least one field change is required")
    private Map<String, Object> changes;
}