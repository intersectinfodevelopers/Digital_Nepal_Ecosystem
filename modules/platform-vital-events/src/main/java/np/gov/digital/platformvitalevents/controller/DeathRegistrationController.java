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
import np.gov.digital.platformvitalevents.dto.DeathApprovalResponse;
import np.gov.digital.platformvitalevents.dto.DeathRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.DeathRegistrationResponse;
import np.gov.digital.platformvitalevents.dto.RejectVitalEventRequest;
import np.gov.digital.platformvitalevents.dto.VerbalAutopsyRequest;
import np.gov.digital.platformvitalevents.exception.DuplicateDeathRecordException;
import np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.service.DeathRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Vital Events — Death", description = "Death registration workflow (SDD Extended Modules §4.3)")
@RestController
@RequestMapping("/v1/vital-events/death")
@RequiredArgsConstructor
@Slf4j
public class DeathRegistrationController {

    private final DeathRegistrationService deathRegistrationService;

    @Operation(
            summary = "Register a death",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. The subject must be an existing, "
                    + "active citizen. A citizen can have at most one non-rejected death registration.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Death registration submitted for approval"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid field"),
            @ApiResponse(responseCode = "404", description = "Ward or citizen does not exist"),
            @ApiResponse(responseCode = "409", description = "ERR_DEATH_ALREADY_RECORDED — this citizen already has an active death registration")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<DeathRegistrationResponse> register(
            @Valid @RequestBody DeathRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deathRegistrationService.register(request));
    }

    @Operation(
            summary = "Attach a verbal autopsy interview to a death registration",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Used when the death occurred outside "
                    + "a health facility with no other certified cause (WHO 2016 VA instrument). Can be "
                    + "submitted before or after the death event itself is approved/rejected.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Verbal autopsy attached"),
            @ApiResponse(responseCode = "404", description = "No death registration with this ID")
    })
    @PostMapping("/{id}/verbal-autopsy")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<Void> attachVerbalAutopsy(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            @Valid @RequestBody VerbalAutopsyRequest request) {
        deathRegistrationService.attachVerbalAutopsy(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
            summary = "Approve a pending death registration",
            description = "Requires LOCAL_BODY_ADMIN role. On approval: the citizen's status becomes DECEASED; "
                    + "if they have a spouse on file, that spouse's eligibility is re-evaluated; any household "
                    + "they were head of is flagged (not auto-reassigned) as needing a new head. ID-card "
                    + "revocation and benefit closure are NOT yet performed — see SDD §4.3 vs. this service's "
                    + "class Javadoc for why (no id_card or benefit/eligibility persistence exists yet).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approved — cascade summary returned"),
            @ApiResponse(responseCode = "403", description = "Approver is the same person who submitted it"),
            @ApiResponse(responseCode = "404", description = "No vital event with this ID"),
            @ApiResponse(responseCode = "409", description = "Event is not in a state that can be approved, "
                    + "or another death for this citizen was already approved")
    })
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<DeathApprovalResponse> approve(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            Authentication authentication) {
        UUID approverId = actorId(authentication);
        return ResponseEntity.ok(deathRegistrationService.approve(id, approverId));
    }

    @Operation(
            summary = "Reject a pending death registration",
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
        deathRegistrationService.reject(id, approverId, request.getReason());
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

    @ExceptionHandler(DuplicateDeathRecordException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateDeath(DuplicateDeathRecordException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ERR_DEATH_ALREADY_RECORDED", "message", ex.getMessage(), "status", "409"));
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
