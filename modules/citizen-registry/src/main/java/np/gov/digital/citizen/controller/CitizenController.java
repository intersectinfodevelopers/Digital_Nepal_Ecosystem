package np.gov.digital.citizen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.CitizenProfileResponse;
import np.gov.digital.citizen.dto.CitizenRegistrationRequest;
import np.gov.digital.citizen.dto.CitizenRegistrationResponse;
import np.gov.digital.citizen.dto.CitizenSummaryResponse;
import np.gov.digital.citizen.enums.CitizenStatus;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.DuplicateCitizenshipException;
import np.gov.digital.citizen.exception.DuplicateNidException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.service.CitizenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Citizen Registry", description = "Citizen registration and record management")
@RestController
@RequestMapping("/v1/citizens")
@RequiredArgsConstructor
@Slf4j
public class CitizenController {
    private final CitizenService citizenService;

    @Operation(
            summary = "List citizens in a ward",
            description = "Paginated. Requires WARD_ADMIN or LOCAL_BODY_ADMIN role; row-level security "
                    + "scopes results to the admin's geography regardless of the wardId passed here.")
    @GetMapping
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<Page<CitizenSummaryResponse>> listByWard(
            @Parameter(description = "Ward ID") @RequestParam UUID wardId,
            Pageable pageable) {
        return ResponseEntity.ok(citizenService.listByWard(wardId, pageable));
    }

    @Operation(
            summary = "Get a citizen's profile",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Decrypts DOB/phone/email for "
                    + "display; citizenship number is returned masked, NID is never returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "404", description = "No active citizen with this ID")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<CitizenProfileResponse> getProfile(
            @Parameter(description = "Citizen ID") @PathVariable UUID id) {
        return ResponseEntity.ok(citizenService.getProfile(id));
    }

    @Operation(
            summary = "Deactivate a citizen record",
            description = "Soft-delete only — never a hard delete. Requires LOCAL_BODY_ADMIN role. "
                    + "voidStatus must be VOIDED_DUPLICATE or VOIDED_FRAUD; DECEASED and "
                    + "RENOUNCED_CITIZENSHIP go through their own dedicated vital-event workflows, not this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deactivated"),
            @ApiResponse(responseCode = "400", description = "voidStatus was not VOIDED_DUPLICATE or VOIDED_FRAUD"),
            @ApiResponse(responseCode = "404", description = "No active citizen with this ID")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Citizen ID") @PathVariable UUID id,
            @Parameter(description = "VOIDED_DUPLICATE or VOIDED_FRAUD") @RequestParam CitizenStatus voidStatus,
            @Parameter(description = "Reason for deactivation, optional") @RequestParam(required = false) String reason) {
        citizenService.deactivate(id, voidStatus, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Register a new citizen",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Citizen registered"),
            @ApiResponse(responseCode = "404", description = "Ward does not exist"),
            @ApiResponse(responseCode = "409", description = "A citizen with this NID is already registered")
    })
    // POST /api/v1/citizens/register
    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<?> registerCitizen(
            @Valid @RequestBody CitizenRegistrationRequest request) {

        log.info("Registration request received for ward: {}", request.getWardId());

        CitizenRegistrationResponse response = citizenService.registerCitizen(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    // EXCEPTION HANDLERS
    @ExceptionHandler(DuplicateNidException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateNid(DuplicateNidException ex) {
        log.warn("Duplicate NID registration blocked");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "DUPLICATE_NID",
                "message", "An active citizen is already registered with this NID",
                "status", "409"
        ));
    }

    // 409 Conflict — ERR_DUPLICATE_CITIZENSHIP (Ext. Modules §9). Citizenship
    // number dedupes independently of NID.
    @ExceptionHandler(DuplicateCitizenshipException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateCitizenship(DuplicateCitizenshipException ex) {
        log.warn("Duplicate citizenship-number registration blocked");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "DUPLICATE_CITIZENSHIP",
                "message", "An active citizen is already registered with this citizenship number",
                "status", "409"
        ));
    }

    // 404 Not Found — ward does not exist.
    @ExceptionHandler(WardNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWardNotFound(WardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "WARD_NOT_FOUND",
                "message", ex.getMessage(),
                "status", "404"
        ));
    }

    // 404 Not Found — citizen does not exist or is deactivated.
    @ExceptionHandler(CitizenNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCitizenNotFound(CitizenNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CITIZEN_NOT_FOUND",
                "message", ex.getMessage(),
                "status", "404"
        ));
    }

    // 400 Bad Request — e.g. deactivate() called with a voidStatus outside
    // the allowed VOIDED_DUPLICATE/VOIDED_FRAUD set.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "INVALID_REQUEST",
                "message", ex.getMessage(),
                "status", "400"
        ));
    }
}
