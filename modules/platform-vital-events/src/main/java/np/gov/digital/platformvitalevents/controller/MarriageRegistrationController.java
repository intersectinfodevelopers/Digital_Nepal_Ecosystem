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
import np.gov.digital.platformvitalevents.dto.MarriageApprovalResponse;
import np.gov.digital.platformvitalevents.dto.MarriageRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.MarriageRegistrationResponse;
import np.gov.digital.platformvitalevents.dto.RejectVitalEventRequest;
import np.gov.digital.platformvitalevents.exception.AlreadyMarriedException;
import np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.UnderageMarriageException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.service.MarriageRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Vital Events — Marriage", description = "Marriage registration workflow (SDD Extended Modules §4.4)")
@RestController
@RequestMapping("/v1/vital-events/marriage")
@RequiredArgsConstructor
@Slf4j
public class MarriageRegistrationController {

    private final MarriageRegistrationService marriageRegistrationService;

    @Operation(
            summary = "Register a marriage",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Gender-neutral — spouse1/spouse2, "
                    + "not husband/wife. Both spouses must be existing, active citizens aged 20 or older at "
                    + "the marriage date, and neither may already be married — both hard-blocked, not "
                    + "overridable by any role. An optional relocatingCitizenId names which spouse (if "
                    + "either) moves to the other's ward; cross-municipality relocation is rejected here — "
                    + "it needs the two-party transfer handoff, not yet implemented.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Marriage registration submitted for approval"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid field, self-marriage, inactive citizen, or unsupported cross-municipality relocation"),
            @ApiResponse(responseCode = "404", description = "Ward or a spouse's citizen ID does not exist"),
            @ApiResponse(responseCode = "409", description = "ERR_MARRIAGE_UNDERAGE or ERR_MARRIAGE_ALREADY_MARRIED")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<MarriageRegistrationResponse> register(
            @Valid @RequestBody MarriageRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(marriageRegistrationService.register(request));
    }

    @Operation(
            summary = "Approve a pending marriage registration",
            description = "Requires LOCAL_BODY_ADMIN role. On approval: both spouses' marital status becomes "
                    + "MARRIED with a bidirectional spouse link; the named relocating spouse (if any) is "
                    + "transferred to the other's ward; a marital_status_history row is written for each "
                    + "spouse. Underage/bigamy are re-checked immediately before applying this, closing the "
                    + "race window against a second concurrent approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approved — cascade summary returned"),
            @ApiResponse(responseCode = "403", description = "Approver is the same person who submitted it"),
            @ApiResponse(responseCode = "404", description = "No vital event with this ID"),
            @ApiResponse(responseCode = "409", description = "Event not in an approvable state, or ERR_MARRIAGE_ALREADY_MARRIED on re-check")
    })
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<MarriageApprovalResponse> approve(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            Authentication authentication) {
        UUID approverId = actorId(authentication);
        return ResponseEntity.ok(marriageRegistrationService.approve(id, approverId));
    }

    @Operation(
            summary = "Reject a pending marriage registration",
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
        marriageRegistrationService.reject(id, approverId, request.getReason());
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

    @ExceptionHandler(UnderageMarriageException.class)
    public ResponseEntity<Map<String, String>> handleUnderage(UnderageMarriageException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ERR_MARRIAGE_UNDERAGE", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(AlreadyMarriedException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyMarried(AlreadyMarriedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ERR_MARRIAGE_ALREADY_MARRIED", "message", ex.getMessage(), "status", "409"));
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
