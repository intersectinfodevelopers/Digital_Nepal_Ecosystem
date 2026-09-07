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
import np.gov.digital.platformvitalevents.dto.MigrationConfirmationResponse;
import np.gov.digital.platformvitalevents.dto.MigrationRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.MigrationRegistrationResponse;
import np.gov.digital.platformvitalevents.dto.RejectVitalEventRequest;
import np.gov.digital.platformvitalevents.exception.InvalidVitalEventTransitionException;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.TransferPendingException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.exception.WrongMunicipalityException;
import np.gov.digital.platformvitalevents.service.MigrationRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Vital Events — Migration", description = "Cross-municipality ward transfer with a two-party handoff (SDD Extended Modules §4.6)")
@RestController
@RequestMapping("/v1/vital-events/migration")
@RequiredArgsConstructor
@Slf4j
public class MigrationRegistrationController {

    private final MigrationRegistrationService migrationRegistrationService;

    @Operation(
            summary = "Request a cross-municipality transfer",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. The citizen's current ward is read "
                    + "from their own record, not the request body. Source and destination wards must be in "
                    + "different municipalities — a same-municipality move is out of scope here.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Migration request submitted, awaiting both confirmations"),
            @ApiResponse(responseCode = "400", description = "Same ward, or same municipality"),
            @ApiResponse(responseCode = "404", description = "Citizen or destination ward does not exist"),
            @ApiResponse(responseCode = "409", description = "ERR_TRANSFER_PENDING — this citizen already has a migration in progress")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<MigrationRegistrationResponse> register(
            @Valid @RequestBody MigrationRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(migrationRegistrationService.register(request));
    }

    @Operation(
            summary = "Confirm release from the losing municipality",
            description = "Requires LOCAL_BODY_ADMIN role, and specifically a Local Body Admin of the ward's "
                    + "current (losing) municipality — not just any LOCAL_BODY_ADMIN. The transfer is applied "
                    + "only once both this and the receiving confirmation are in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmed — transferCompleted is true only if the receiving side had already confirmed too"),
            @ApiResponse(responseCode = "403", description = "Wrong municipality, or the same person who submitted the request"),
            @ApiResponse(responseCode = "404", description = "No migration request with this ID"),
            @ApiResponse(responseCode = "409", description = "Event is not in a confirmable state")
    })
    @PostMapping("/{id}/confirm-losing")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<MigrationConfirmationResponse> confirmLosing(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(migrationRegistrationService.confirmLosing(id, actorId(authentication)));
    }

    @Operation(
            summary = "Confirm reception into the receiving municipality",
            description = "Requires LOCAL_BODY_ADMIN role, and specifically a Local Body Admin of the "
                    + "destination (receiving) municipality. The transfer is applied only once both this and "
                    + "the losing confirmation are in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmed — transferCompleted is true only if the losing side had already confirmed too"),
            @ApiResponse(responseCode = "403", description = "Wrong municipality, or the same person who submitted the request"),
            @ApiResponse(responseCode = "404", description = "No migration request with this ID"),
            @ApiResponse(responseCode = "409", description = "Event is not in a confirmable state")
    })
    @PostMapping("/{id}/confirm-receiving")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<MigrationConfirmationResponse> confirmReceiving(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(migrationRegistrationService.confirmReceiving(id, actorId(authentication)));
    }

    @Operation(
            summary = "Reject a pending migration request",
            description = "Requires LOCAL_BODY_ADMIN role, from either the losing or the receiving "
                    + "municipality. The submitter can never be the one who rejects it.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rejected"),
            @ApiResponse(responseCode = "403", description = "Not a Local Body Admin of either municipality"),
            @ApiResponse(responseCode = "404", description = "No migration request with this ID"),
            @ApiResponse(responseCode = "409", description = "Event is not in a state that can be rejected")
    })
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<Void> reject(
            @Parameter(description = "Vital event ID") @PathVariable UUID id,
            @Valid @RequestBody RejectVitalEventRequest request,
            Authentication authentication) {
        migrationRegistrationService.reject(id, actorId(authentication), request.getReason());
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

    @ExceptionHandler(TransferPendingException.class)
    public ResponseEntity<Map<String, String>> handleTransferPending(TransferPendingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ERR_TRANSFER_PENDING", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(WrongMunicipalityException.class)
    public ResponseEntity<Map<String, String>> handleWrongMunicipality(WrongMunicipalityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "WRONG_MUNICIPALITY", "message", ex.getMessage(), "status", "403"));
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
