package np.gov.digital.citizen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.EligibilityResponse;
import np.gov.digital.citizen.service.EligibilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for benefit eligibility.
 * Base path: /api/v1/citizens/{id}/eligibility
 */
@Tag(name = "Eligibility", description = "Benefit-programme and ID-card eligibility evaluation")
@RestController
@RequestMapping("/v1/citizens")
@RequiredArgsConstructor
@Slf4j
public class EligibilityController {

    private final EligibilityService eligibilityService;

    /**
     * GET /api/v1/citizens/{id}/eligibility
     * Runs the eligibility engine and returns all benefit programmes
     * and ID card types the citizen qualifies for.
     *
     * Access: WARD_ADMIN, LOCAL_BODY_ADMIN
     * RLS ensures citizen is within the admin's geographic scope.
     */
    @Operation(
            summary = "Evaluate benefit eligibility for a citizen",
            description = "Runs the eligibility engine and returns all benefit programmes and ID card types "
                    + "the citizen qualifies for. Requires WARD_ADMIN or LOCAL_BODY_ADMIN role; row-level "
                    + "security scopes results to the admin's geography.")
    @GetMapping("/{id}/eligibility")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<EligibilityResponse> getEligibility(
            @Parameter(description = "Citizen ID") @PathVariable UUID id) {
        log.info("Eligibility check requested for citizen: {}", id);
        EligibilityResponse response = eligibilityService.evaluate(id);
        return ResponseEntity.ok(response);
    }
}
