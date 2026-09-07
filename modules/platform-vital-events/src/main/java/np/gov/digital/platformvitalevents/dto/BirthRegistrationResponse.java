package np.gov.digital.platformvitalevents.dto;

import lombok.*;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Output for POST /v1/vital-events/birth. Unlike duplicate NID/citizenship
 * (a hard 409 block), a fuzzy name/DOB match on a birth submission is
 * informational only — SDD §4.2 calls for the Ward/Local Body Admin to
 * see and judge it, never an automatic rejection, since two different
 * newborns sharing a name and birthday in the same ward is uncommon but
 * real.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BirthRegistrationResponse {
    private UUID vitalEventId;
    private VitalEventStatus status;
    private String childNameEn;
    private UUID wardId;
    private Instant submittedAt;

    @Builder.Default
    private List<UUID> possibleDuplicateVitalEventIds = List.of();

    private String message;
}
