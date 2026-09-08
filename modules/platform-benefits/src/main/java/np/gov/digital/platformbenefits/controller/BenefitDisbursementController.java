package np.gov.digital.platformbenefits.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.platformbenefits.dto.DisburseRequest;
import np.gov.digital.platformbenefits.dto.DisbursementCallbackRequest;
import np.gov.digital.platformbenefits.dto.DisbursementResponse;
import np.gov.digital.platformbenefits.dto.EligibleCitizenResponse;
import np.gov.digital.platformbenefits.enums.BenefitType;
import np.gov.digital.platformbenefits.exception.DisbursementNotFoundException;
import np.gov.digital.platformbenefits.exception.DuplicateDisbursementException;
import np.gov.digital.platformbenefits.exception.IneligibleCitizenException;
import np.gov.digital.platformbenefits.exception.InvalidPaymentTransitionException;
import np.gov.digital.platformbenefits.service.BenefitDisbursementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Tag(name = "Benefits", description = "Government-to-Person cash disbursement (Governance Tiers §8)")
@RestController
@RequestMapping("/v1/benefits")
@RequiredArgsConstructor
public class BenefitDisbursementController {

    private final BenefitDisbursementService benefitDisbursementService;

    // Empty string when unset (see application.yml's own comment on why
    // not the SpEL #{null} idiom) — isBlank() treats that the same as
    // truly missing: always reject.
    @Value("${benefits.callback.secret:}")
    private String callbackSecret;

    @Operation(
            summary = "Initiate a disbursement for an eligible citizen",
            description = "Requires LOCAL_BODY_ADMIN role. The citizen must currently be eligible for "
                    + "benefitType (re-checked here, not just at eligible-list time), and must not already "
                    + "have an active (non-failed/cancelled) disbursement for the same benefitType and period.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Disbursement initiated"),
            @ApiResponse(responseCode = "404", description = "Citizen does not exist"),
            @ApiResponse(responseCode = "409", description = "Citizen is not eligible, or already has an active disbursement for this period (ERR_DUPLICATE_DISBURSEMENT)")
    })
    @PostMapping("/{citizenId}/disburse")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<DisbursementResponse> disburse(
            @Parameter(description = "Citizen ID") @PathVariable UUID citizenId,
            @Valid @RequestBody DisburseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(benefitDisbursementService.disburse(citizenId, request));
    }

    @Operation(
            summary = "Payment-rail settlement callback",
            description = "Public endpoint (external webhook) — authenticated via a shared secret header "
                    + "(X-Callback-Secret), not the mTLS the design doc calls for elsewhere, because no real "
                    + "payment-rail integration exists yet to hold a certificate against. See "
                    + "BenefitDisbursementService's class Javadoc for why.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback applied"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong X-Callback-Secret"),
            @ApiResponse(responseCode = "404", description = "No disbursement with this ID"),
            @ApiResponse(responseCode = "409", description = "Disbursement is not in a state that can be settled/failed")
    })
    @PostMapping("/disburse/{id}/callback")
    public ResponseEntity<DisbursementResponse> callback(
            @Parameter(description = "Disbursement ID") @PathVariable UUID id,
            @RequestHeader(value = "X-Callback-Secret", required = false) String providedSecret,
            @Valid @RequestBody DisbursementCallbackRequest callback) {

        if (callbackSecret == null || callbackSecret.isBlank() || !callbackSecret.equals(providedSecret)) {
            log.warn("BenefitDisbursementController: rejected callback for disbursement {} — bad/missing secret", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(benefitDisbursementService.applyCallback(id, callback));
    }

    @Operation(
            summary = "Get a disbursement's current status",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status returned"),
            @ApiResponse(responseCode = "404", description = "No disbursement with this ID")
    })
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<DisbursementResponse> getStatus(
            @Parameter(description = "Disbursement ID") @PathVariable UUID id) {
        return ResponseEntity.ok(benefitDisbursementService.getStatus(id));
    }

    @Operation(
            summary = "List a citizen's disbursement history",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role.")
    @GetMapping("/citizen/{citizenId}")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<Page<DisbursementResponse>> listByCitizen(
            @Parameter(description = "Citizen ID") @PathVariable UUID citizenId, Pageable pageable) {
        return ResponseEntity.ok(benefitDisbursementService.listByCitizen(citizenId, pageable));
    }

    @Operation(
            summary = "List citizens in a ward eligible for a benefit but not yet disbursed for a period",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Re-evaluates eligibility per "
                    + "citizen at request time (no bulk eligibility cache exists) — not a real-time feed, "
                    + "safe to re-poll.")
    @GetMapping("/eligible-list")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<List<EligibleCitizenResponse>> eligibleList(
            @RequestParam UUID wardId,
            @RequestParam BenefitType benefitType,
            @RequestParam String period,
            Pageable pageable) {
        return ResponseEntity.ok(benefitDisbursementService.eligibleList(wardId, benefitType, period, pageable));
    }

    // ---------------------------------------------------------------
    // EXCEPTION HANDLERS
    // ---------------------------------------------------------------

    @ExceptionHandler(CitizenNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCitizenNotFound(CitizenNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CITIZEN_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(DisbursementNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleDisbursementNotFound(DisbursementNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "DISBURSEMENT_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(IneligibleCitizenException.class)
    public ResponseEntity<Map<String, String>> handleIneligible(IneligibleCitizenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CITIZEN_NOT_ELIGIBLE", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(DuplicateDisbursementException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateDisbursementException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ERR_DUPLICATE_DISBURSEMENT", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(InvalidPaymentTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(InvalidPaymentTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_PAYMENT_TRANSITION", "message", ex.getMessage(), "status", "409"));
    }
}
