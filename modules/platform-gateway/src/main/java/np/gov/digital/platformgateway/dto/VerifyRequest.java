package np.gov.digital.platformgateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * The relying party already knows citizenId from an out-of-band exchange
 * with the citizen themselves (e.g. the citizen presents their NID at
 * the relying party's counter) — the gateway never hands out a citizen's
 * real ID to a relying party first; verify() is always initiated with
 * one already in hand.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyRequest {

    @NotNull(message = "citizenId is required")
    private UUID citizenId;
}
