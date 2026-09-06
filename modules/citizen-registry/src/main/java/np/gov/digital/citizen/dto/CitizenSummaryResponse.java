package np.gov.digital.citizen.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Row shape for GET /v1/citizens (list view). Deliberately excludes every
 * encrypted field — NID, citizenship number, DOB, phone, email — a list
 * view has no business decrypting PII for every row on a page. Fetch
 * {@link CitizenProfileResponse} for one citizen when the full record is
 * actually needed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenSummaryResponse {
    private UUID citizenId;
    private String nameNp;
    private String nameEn;
    private UUID wardId;
    private String sex;
    private Boolean nidVerified;
    private Boolean isActive;
    private String syncStatus;
    private Instant registeredAt;
}
