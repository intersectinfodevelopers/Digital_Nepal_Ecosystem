package np.gov.digital.platformevidenceexchange.controller;

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
import np.gov.digital.platformevidenceexchange.dto.CreateEvidenceRequestRequest;
import np.gov.digital.platformevidenceexchange.dto.EvidenceRequestCallbackRequest;
import np.gov.digital.platformevidenceexchange.dto.EvidenceRequestResponse;
import np.gov.digital.platformevidenceexchange.exception.EvidenceRequestNotFoundException;
import np.gov.digital.platformevidenceexchange.exception.InvalidEvidenceRequestTransitionException;
import np.gov.digital.platformevidenceexchange.exception.MismatchedPurposeAgencyException;
import np.gov.digital.platformevidenceexchange.service.EvidenceRequestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Tag(name = "Evidence Exchange", description = "Once-Only Evidence Exchange with DAO/NIDMC/Election Commission (Governance Tiers §7)")
@RestController
@RequestMapping("/v1/evidence-exchange")
@RequiredArgsConstructor
public class EvidenceRequestController {

    private final EvidenceRequestService evidenceRequestService;

    @Value("${evidence-exchange.callback.secret:}")
    private String callbackSecret;

    @Operation(
            summary = "Create an evidence request",
            description = "Requires LOCAL_BODY_ADMIN role. One citizen, one fact, one agency per request — "
                    + "never a bulk export. purpose must match targetAgency: CITIZENSHIP_VERIFICATION -> DAO, "
                    + "NID_VERIFICATION -> NIDMC, VOTER_ROLL_CHECK -> ELECTION_COMMISSION.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request created, status PENDING"),
            @ApiResponse(responseCode = "404", description = "Citizen does not exist"),
            @ApiResponse(responseCode = "409", description = "purpose does not match targetAgency")
    })
    @PostMapping("/requests")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<EvidenceRequestResponse> create(
            @Valid @RequestBody CreateEvidenceRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evidenceRequestService.create(request));
    }

    @Operation(
            summary = "Get a single evidence request",
            description = "Requires LOCAL_BODY_ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request returned"),
            @ApiResponse(responseCode = "404", description = "No request with this ID")
    })
    @GetMapping("/requests/{id}")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<EvidenceRequestResponse> getById(
            @Parameter(description = "Evidence request ID") @PathVariable UUID id) {
        return ResponseEntity.ok(evidenceRequestService.getById(id));
    }

    @Operation(
            summary = "Audit list of every evidence request made",
            description = "Requires CENTRAL_ADMIN role — a national-scope audit view of every request made "
                    + "system-wide, not scoped to one municipality. Read-only, paginated; not a bulk-export "
                    + "mechanism for the requested facts themselves, only for reviewing that requests were "
                    + "made appropriately.")
    @GetMapping("/requests")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<Page<EvidenceRequestResponse>> auditList(Pageable pageable) {
        return ResponseEntity.ok(evidenceRequestService.auditList(pageable));
    }

    @Operation(
            summary = "Target agency's response callback",
            description = "Public endpoint (external webhook) — authenticated via a shared secret header "
                    + "(X-Callback-Secret), not the mTLS the design doc calls for. No real DAO/NIDMC/Election "
                    + "Commission integration exists to hold a certificate against — see "
                    + "EvidenceRequestService's class Javadoc.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback applied"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong X-Callback-Secret"),
            @ApiResponse(responseCode = "404", description = "No request with this ID"),
            @ApiResponse(responseCode = "409", description = "Request is not PENDING")
    })
    @PostMapping("/requests/{id}/callback")
    public ResponseEntity<EvidenceRequestResponse> callback(
            @Parameter(description = "Evidence request ID") @PathVariable UUID id,
            @RequestHeader(value = "X-Callback-Secret", required = false) String providedSecret,
            @Valid @RequestBody EvidenceRequestCallbackRequest callback) {

        if (callbackSecret == null || callbackSecret.isBlank() || !callbackSecret.equals(providedSecret)) {
            log.warn("EvidenceRequestController: rejected callback for request {} — bad/missing secret", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(evidenceRequestService.applyCallback(id, callback));
    }

    // ---------------------------------------------------------------
    // EXCEPTION HANDLERS
    // ---------------------------------------------------------------

    @ExceptionHandler(CitizenNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCitizenNotFound(CitizenNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CITIZEN_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(EvidenceRequestNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRequestNotFound(EvidenceRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "EVIDENCE_REQUEST_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(MismatchedPurposeAgencyException.class)
    public ResponseEntity<Map<String, String>> handleMismatchedPurpose(MismatchedPurposeAgencyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "PURPOSE_AGENCY_MISMATCH", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(InvalidEvidenceRequestTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(InvalidEvidenceRequestTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_EVIDENCE_REQUEST_TRANSITION", "message", ex.getMessage(), "status", "409"));
    }
}
