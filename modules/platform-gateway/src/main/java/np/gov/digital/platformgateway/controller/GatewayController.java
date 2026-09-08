package np.gov.digital.platformgateway.controller;

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
import np.gov.digital.platformgateway.dto.ConsentConfirmRequest;
import np.gov.digital.platformgateway.dto.ConsentInitiateRequest;
import np.gov.digital.platformgateway.dto.VerifyRequest;
import np.gov.digital.platformgateway.dto.VerifyResponse;
import np.gov.digital.platformgateway.exception.ConsentRequiredException;
import np.gov.digital.platformgateway.exception.InvalidClientCredentialsException;
import np.gov.digital.platformgateway.exception.InvalidOtpException;
import np.gov.digital.platformgateway.exception.RelyingPartyNotFoundException;
import np.gov.digital.platformgateway.exception.RelyingPartySuspendedException;
import np.gov.digital.platformgateway.exception.ScopeDeniedException;
import np.gov.digital.platformgateway.service.GatewayConsentService;
import np.gov.digital.platformgateway.service.GatewayVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * The relying-party-facing surface of the gateway (Extended Modules
 * §6.3-6.4) — every endpoint here is called by an external organization,
 * never by a citizen/admin JWT holder, and is authenticated instead via
 * the X-Client-Id/X-Client-Secret headers checked against the licensing
 * record created through RelyingPartyController. See
 * GatewayVerificationService's class Javadoc for why this, not full
 * OAuth2 client-credentials, is the checked boundary. Spring Security
 * itself permits these paths (SecurityConfig) since the JWT filter has
 * nothing to authenticate here — the credential check happens inside
 * the service layer instead.
 */
@Slf4j
@Tag(name = "Relying Party Gateway - Verify & Consent", description = "The relying-party-facing verify/consent calls of the External Relying-Party Gateway (Extended Modules §6.3-6.4)")
@RestController
@RequestMapping("/v1/gateway")
@RequiredArgsConstructor
@SecurityRequirements
public class GatewayController {

    private final GatewayVerificationService gatewayVerificationService;
    private final GatewayConsentService gatewayConsentService;

    @Operation(
            summary = "Verify one purpose-scoped fact about a citizen",
            description = "Authenticated via X-Client-Id/X-Client-Secret headers, not a citizen/admin JWT. "
                    + "Returns only the fields this relying party is licensed for this purpose, via this "
                    + "relying party's own pairwise token for the citizen — never the citizen's real ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verified — whitelisted fields returned"),
            @ApiResponse(responseCode = "401", description = "Invalid client credentials"),
            @ApiResponse(responseCode = "403", description = "Relying party suspended/revoked or certificate lapsed (ERR_RELYING_PARTY_SUSPENDED)"),
            @ApiResponse(responseCode = "404", description = "Citizen does not exist"),
            @ApiResponse(responseCode = "409", description = "Not licensed for this purpose (ERR_RELYING_PARTY_SCOPE_DENIED), or citizen consent not confirmed (ERR_RELYING_PARTY_CONSENT_REQUIRED)")
    })
    @PostMapping("/verify/{purposeCode}")
    public ResponseEntity<VerifyResponse> verify(
            @Parameter(description = "Licensed purpose code, e.g. KYC_VERIFICATION") @PathVariable String purposeCode,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret,
            @Valid @RequestBody VerifyRequest request) {
        return ResponseEntity.ok(gatewayVerificationService.verify(clientId, clientSecret, purposeCode, request));
    }

    @Operation(
            summary = "Initiate OTP consent for a citizen/purpose",
            description = "Authenticated via X-Client-Id/X-Client-Secret headers. Sends a 6-digit OTP to the "
                    + "citizen's registered phone (via SparrowSmsService); the relying party relays it back "
                    + "through /consent/confirm — there is no separate citizen-facing channel in this backend.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent, consent PENDING"),
            @ApiResponse(responseCode = "401", description = "Invalid client credentials"),
            @ApiResponse(responseCode = "404", description = "Citizen does not exist")
    })
    @PostMapping("/consent/initiate")
    public ResponseEntity<Map<String, UUID>> initiateConsent(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret,
            @Valid @RequestBody ConsentInitiateRequest request) {
        UUID consentId = gatewayConsentService.initiate(clientId, clientSecret, request);
        return ResponseEntity.ok(Map.of("consentId", consentId));
    }

    @Operation(
            summary = "Confirm OTP consent for a citizen/purpose",
            description = "Authenticated via X-Client-Id/X-Client-Secret headers. Must match the most recent "
                    + "PENDING consent request initiated for this exact citizen/purpose by this same relying party.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consent CONFIRMED"),
            @ApiResponse(responseCode = "401", description = "Invalid client credentials"),
            @ApiResponse(responseCode = "409", description = "No pending consent, OTP incorrect, or OTP expired")
    })
    @PostMapping("/consent/confirm")
    public ResponseEntity<Void> confirmConsent(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret,
            @Valid @RequestBody ConsentConfirmRequest request) {
        gatewayConsentService.confirm(clientId, clientSecret, request);
        return ResponseEntity.ok().build();
    }

    // ---------------------------------------------------------------
    // EXCEPTION HANDLERS
    // ---------------------------------------------------------------

    @ExceptionHandler(InvalidClientCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidClientCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "INVALID_CLIENT_CREDENTIALS", "message", ex.getMessage(), "status", "401"));
    }

    @ExceptionHandler(RelyingPartySuspendedException.class)
    public ResponseEntity<Map<String, String>> handleSuspended(RelyingPartySuspendedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "ERR_RELYING_PARTY_SUSPENDED", "message", ex.getMessage(), "status", "403"));
    }

    @ExceptionHandler(ScopeDeniedException.class)
    public ResponseEntity<Map<String, String>> handleScopeDenied(ScopeDeniedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ERR_RELYING_PARTY_SCOPE_DENIED", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<Map<String, String>> handleConsentRequired(ConsentRequiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "ERR_RELYING_PARTY_CONSENT_REQUIRED", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOtp(InvalidOtpException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_OTP", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(CitizenNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCitizenNotFound(CitizenNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CITIZEN_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(RelyingPartyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRelyingPartyNotFound(RelyingPartyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "RELYING_PARTY_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }
}
