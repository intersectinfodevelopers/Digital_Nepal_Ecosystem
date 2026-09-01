package np.gov.digital.citizen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.CitizenRegistrationRequest;
import np.gov.digital.citizen.dto.CitizenRegistrationResponse;
import np.gov.digital.citizen.exception.DuplicateNidException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.service.CitizenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Citizen Registry", description = "Citizen registration and record management")
@RestController
@RequestMapping("/v1/citizens")
@RequiredArgsConstructor
@Slf4j
public class CitizenController {
    private final CitizenService citizenService;

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

    // 404 Not Found — ward does not exist.
    @ExceptionHandler(WardNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWardNotFound(WardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "WARD_NOT_FOUND",
                "message", ex.getMessage(),
                "status", "404"
        ));
    }
}
