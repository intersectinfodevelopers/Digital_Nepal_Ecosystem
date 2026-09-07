package np.gov.digital.platformvitalevents.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformvitalevents.dto.DivorceRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.DivorceRegistrationResponse;
import np.gov.digital.platformvitalevents.dto.RejectVitalEventRequest;
import np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException;
import np.gov.digital.platformvitalevents.exception.NotMarriedToEachOtherException;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.service.DivorceRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Vital Events — Divorce", description = "Divorce registration workflow (SDD Extended Modules §4.5)")
@RestController
@RequestMapping("/v1/vital-events/divorce")
@RequiredArgsConstructor
@Slf4j
public class DivorceRegistrationController {

    private final DivorceRegistrationService divorceRegistrationService;

    @Operation(
            summary = "Register a divorce",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Both citizens must currently be "
                    + "married to each other. Requires a court order reference — courtOrderNo is the order's "
                    + "own reference number, not an uploaded document (no document-storage system exists yet "
                    + "in this codebase). Deliberately never touches residency — no ward transfer, unlike "
                    + "marriage.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Divorce registration submitted for approval"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid field, including a missing court order number (ERR_DIVORCE_NO_COURT_ORDER)"),
            @ApiResponse(responseCode = "404", description = "Ward or a spouse's citizen ID does not exist"),
            @ApiResponse(responseCode = "409", description = "The two citizens are not currently married to each other")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<DivorceRegistrationResponse> register(
            @Valid @RequestBody DivorceRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(divorceRegistrationService.register(request));
    }

    @Operation(
            summary = "Approve a pending divorce registration",
            description = "Requires LOCAL_BODY_ADMIN role. On approval: both citizens' marital status becomes "
                    + "DIVORCED and their spouse link is cleared. No ward transfer is performed.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Approved"),
            @ApiResponse(responseCode = "403", description = "Approver is the same person who submitted it"),
            @ApiResponse(responseCode = "404", description = "No vital event with this ID"),
            @ApiResponse(responseCode = "409", description = "Event not in an approvable state, or the two are no longer married to each other on re-check")
    })
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<Void> approve(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            Authentication authentication) {
        UUID approverId = actorId(authentication);
        divorceRegistrationService.approve(id, approverId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Reject a pending divorce registration",
            description = "Requires LOCAL_BODY_ADMIN role. The submitter can never be the one who rejects it.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rejected"),
            @ApiResponse(responseCode = "403", description = "Approver is the same person who submitted it"),
            @ApiResponse(responseCode = "404", description = "No vital event with this ID"),
            @ApiResponse(responseCode = "409", description = "Event is not in a state that can be rejected")
    })
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<Void> reject(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            @Valid @RequestBody RejectVitalEventRequest request,
            Authentication authentication) {
        UUID approverId = actorId(authentication);
        divorceRegistrationService.reject(id, approverId, request.getReason());
        return ResponseEntity.noContent().build();
    }

    private UUID actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            return actor.getUserId();
        }
        throw new IllegalStateException("No authenticated actor in security context.");
    }

    // ---------------------------------------------------------------
    // EXCEPTION HANDLERS
    // ---------------------------------------------------------------

    @ExceptionHandler(WardNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWardNotFound(WardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "WARD_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(CitizenNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCitizenNotFound(CitizenNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CITIZEN_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(VitalEventNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleVitalEventNotFound(VitalEventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "VITAL_EVENT_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(NotMarriedToEachOtherException.class)
    public ResponseEntity<Map<String, String>> handleNotMarried(NotMarriedToEachOtherException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "NOT_MARRIED_TO_EACH_OTHER", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(SelfApprovalException.class)
    public ResponseEntity<Map<String, String>> handleSelfApproval(SelfApprovalException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "SELF_APPROVAL_FORBIDDEN", "message", ex.getMessage(), "status", "403"));
    }

    @ExceptionHandler(InvalidVitalEventTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(InvalidVitalEventTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_VITAL_EVENT_TRANSITION", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "INVALID_REQUEST", "message", ex.getMessage(), "status", "400"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_STATE", "message", ex.getMessage(), "status", "409"));
    }
}
