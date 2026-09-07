package np.gov.digital.platformvitalevents.dto;

import lombok.*;

import java.util.UUID;

/**
 * What actually happened as a result of approving a death event — see
 * DeathRegistrationService.approve()'s Javadoc for which parts of SDD
 * §4.3's full cascade (citizen archived, ID cards revoked, benefits
 * closed, spouse eligibility re-run, head-of-household reassignment
 * flagged) are wired up in this increment versus still pending
 * infrastructure that doesn't exist yet.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeathApprovalResponse {
    private UUID citizenId;
    private boolean spouseEligibilityReevaluated;
    private UUID spouseCitizenId;
    private int householdsFlaggedForReassignment;
}
