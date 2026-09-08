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
import np.gov.digital.platformvitalevents.dto.BirthRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.BirthRegistrationResponse;
import np.gov.digital.platformvitalevents.dto.RejectVitalEventRequest;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.service.BirthRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Vital Events — Birth", description = "Birth registration workflow (SDD Extended Modules §4.2)")
@RestController
@RequestMapping("/v1/vital-events/birth")
@RequiredArgsConstructor
@Slf4j
public class BirthRegistrationController {

    private final BirthRegistrationService birthRegistrationService;

    @Operation(
            summary = "Register a birth",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Creates a vital_event + birth_record "
                    + "pending Local Body Admin approval. A fuzzy name/DOB match against existing birth "
                    + "records in the same ward is flagged in the response but never blocks submission.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Birth registration submitted for approval"),
            @ApiResponse(responseCode = "400", description = "Missing required field, or neither parent identified"),
            @ApiResponse(responseCode = "404", description = "Ward, father, or mother citizen ID does not exist")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<BirthRegistrationResponse> register(
            @Valid @RequestBody BirthRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(birthRegistrationService.register(request));
    }

    @Operation(
            summary = "Approve a pending birth registration",
            description = "Requires LOCAL_BODY_ADMIN role. Creates the resulting citizen record "
                    + "(registrationStage BIRTH_REGISTERED, no NID/citizenship yet). The submitter can never "
                    + "be the approver, regardless of role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approved — returns the new citizen's ID"),
            @ApiResponse(responseCode = "403", description = "Approver is the same person who submitted it"),
            @ApiResponse(responseCode = "404", description = "No vital event with this ID"),
            @ApiResponse(responseCode = "409", description = "Event is not in a state that can be approved")
    })
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<Map<String, UUID>> approve(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            Authentication authentication) {
        UUID approverId = actorId(authentication);
        UUID childCitizenId = birthRegistrationService.approve(id, approverId);
        return ResponseEntity.ok(Map.of("citizenId", childCitizenId));
    }

    @Operation(
            summary = "Reject a pending birth registration",
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
        birthRegistrationService.reject(id, approverId, request.getReason());
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

    @ExceptionHandler(SelfApprovalException.class)
    public ResponseEntity<Map<String, String>> handleSelfApproval(SelfApprovalException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "SELF_APPROVAL_FORBIDDEN", "message", ex.getMessage(), "status", "403"));
    }

    @ExceptionHandler(np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(
            np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_VITAL_EVENT_TRANSITION", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "INVALID_REQUEST", "message", ex.getMessage(), "status", "400"));
    }
}
