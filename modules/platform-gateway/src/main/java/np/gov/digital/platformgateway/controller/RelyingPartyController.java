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
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformgateway.dto.*;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.entity.RelyingPartyPurpose;
import np.gov.digital.platformgateway.exception.DualSignoffException;
import np.gov.digital.platformgateway.exception.RelyingPartyNotFoundException;
import np.gov.digital.platformgateway.entity.GatewayAccessLog;
import np.gov.digital.platformgateway.repository.GatewayAccessLogRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import np.gov.digital.platformgateway.service.RelyingPartyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central-Admin-facing licensing surface (Extended Modules §6.1-6.2, §6.4-6.5).
 * The relying party's own calls (verify, consent) live in GatewayController
 * instead — those authenticate via client credentials, not a citizen/admin JWT.
 */
@Slf4j
@Tag(name = "Relying Party Gateway - Administration", description = "Licensing, purposes, and revocation for the External Relying-Party Gateway (Extended Modules §6.1-6.5)")
@RestController
@RequestMapping("/v1/admin/relying-parties")
@RequiredArgsConstructor
public class RelyingPartyController {

    private final RelyingPartyService relyingPartyService;
    private final GatewayAccessLogRepository gatewayAccessLogRepository;
    private final RelyingPartyRepository relyingPartyRepository;

    @Operation(
            summary = "License a new relying party",
            description = "Requires CENTRAL_ADMIN role. Returns the ONE-TIME plaintext client secret — "
                    + "it is bcrypt-hashed for storage and can never be retrieved again after this response.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Relying party licensed"),
            @ApiResponse(responseCode = "400", description = "Validation failure")
    })
    @PostMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<CreateRelyingPartyResponse> create(
            @Valid @RequestBody CreateRelyingPartyRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(relyingPartyService.create(request, actorId(authentication)));
    }

    @Operation(
            summary = "Get a relying party's licensing record",
            description = "Requires CENTRAL_ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relying party returned"),
            @ApiResponse(responseCode = "404", description = "No relying party with this ID")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<RelyingPartyResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(relyingPartyService.getById(id));
    }

    @Operation(
            summary = "License a relying party for a purpose",
            description = "Requires CENTRAL_ADMIN role. allowedFields scopes exactly what verify() may ever "
                    + "return for this purpose — never the citizen's full record. Field names match "
                    + "CitizenProfileResponse's own vocabulary (nameEn, nameNp, sex, dob, wardId, status, "
                    + "citizenshipNoMasked, maritalStatus); requiresConsent starts true for every new purpose.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purpose added"),
            @ApiResponse(responseCode = "404", description = "No relying party with this ID")
    })
    @PostMapping("/{id}/purposes")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<PurposeResponse> addPurpose(
            @PathVariable UUID id, @Valid @RequestBody AddPurposeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toPurposeResponse(relyingPartyService.addPurpose(id, request)));
    }

    @Operation(
            summary = "Sign off on a purpose's LEGAL_MANDATE_NO_CONSENT exception",
            description = "Requires CENTRAL_ADMIN role. Two DISTINCT Central Admins must each call this "
                    + "before the purpose's requiresConsent flips to false — the same admin calling twice is "
                    + "rejected. Narrow legal-exception path only (§6.4); every other purpose stays "
                    + "consent-gated via OTP.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sign-off recorded (and applied, if this was the second distinct approver)"),
            @ApiResponse(responseCode = "404", description = "No purpose with this ID"),
            @ApiResponse(responseCode = "409", description = "Same admin already gave the first sign-off, or the exception is already fully approved")
    })
    @PostMapping("/purposes/{purposeId}/approve-no-consent-exception")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<PurposeResponse> approveNoConsentException(
            @PathVariable UUID purposeId, Authentication authentication) {
        return ResponseEntity.ok(
                toPurposeResponse(relyingPartyService.approveNoConsentException(purposeId, actorId(authentication))));
    }

    @Operation(
            summary = "Revoke a relying party",
            description = "Requires CENTRAL_ADMIN role. Terminal — the graduated penalty ladder's final rung "
                    + "(§6.5), never reversible. A revoked relying party fails every subsequent verify() call.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relying party revoked"),
            @ApiResponse(responseCode = "404", description = "No relying party with this ID")
    })
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        relyingPartyService.revoke(id, body.getOrDefault("reason", "No reason given"));
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Public transparency list of revoked relying parties",
            description = "No authentication required — citizens and the public can see which "
                    + "organizations have lost their license to verify facts through the gateway.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Revoked relying parties returned")
    @GetMapping("/revoked")
    public ResponseEntity<List<RelyingPartyResponse>> listRevoked() {
        List<RelyingPartyResponse> revoked = relyingPartyService.listRevoked().stream()
                .map(rp -> RelyingPartyResponse.builder()
                        .id(rp.getId())
                        .name(rp.getName())
                        .organizationType(rp.getOrganizationType())
                        .status(rp.getStatus())
                        .suspensionReason(rp.getSuspensionReason())
                        .licensedAt(rp.getLicensedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(revoked);
    }

    @Operation(
            summary = "A citizen's own gateway access log",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role — this backend has no citizen "
                    + "self-service portal, so the citizen-facing transparency record (§6.5) is surfaced "
                    + "through the ward/local-body staff who assist that citizen, same access pattern as the "
                    + "rest of this backend's citizen data.")
    @ApiResponse(responseCode = "200", description = "Access log returned, most recent first")
    @GetMapping("/citizens/{citizenId}/access-log")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<Page<AccessLogResponse>> citizenAccessLog(
            @Parameter(description = "Citizen ID") @PathVariable UUID citizenId, Pageable pageable) {
        Page<GatewayAccessLog> logs = gatewayAccessLogRepository.findByCitizen_IdOrderByAccessedAtDesc(citizenId, pageable);

        // gateway_access_log stores relyingPartyId as a bare UUID (not an
        // entity relation, kept deliberately simple) — found live, this
        // meant the "transparency record" a ward/local-body staffer reads
        // showed only a UUID, never the organization's actual name.
        // Batch-resolved here rather than one lookup per row.
        Map<UUID, String> namesById = relyingPartyRepository
                .findAllById(logs.getContent().stream().map(GatewayAccessLog::getRelyingPartyId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(RelyingParty::getId, RelyingParty::getName));

        Page<AccessLogResponse> page = logs.map(log -> AccessLogResponse.builder()
                .relyingPartyId(log.getRelyingPartyId())
                .relyingPartyName(namesById.get(log.getRelyingPartyId()))
                .purposeCode(log.getPurposeCode())
                .fieldsDisclosed(log.getFieldsDisclosed())
                .consentMethod(log.getConsentMethod())
                .outcome(log.getOutcome())
                .accessedAt(log.getAccessedAt())
                .build());
        return ResponseEntity.ok(page);
    }

    private PurposeResponse toPurposeResponse(RelyingPartyPurpose purpose) {
        return PurposeResponse.builder()
                .id(purpose.getId())
                .relyingPartyId(purpose.getRelyingParty().getId())
                .purposeCode(purpose.getPurposeCode())
                .allowedFields(purpose.getAllowedFields())
                .requiresConsent(purpose.getRequiresConsent())
                .noConsentApprovedBy1(purpose.getNoConsentApprovedBy1())
                .noConsentApprovedBy2(purpose.getNoConsentApprovedBy2())
                .status(purpose.getStatus().name())
                .createdAt(purpose.getCreatedAt())
                .updatedAt(purpose.getUpdatedAt())
                .build();
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

    @ExceptionHandler(RelyingPartyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RelyingPartyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "RELYING_PARTY_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(DualSignoffException.class)
    public ResponseEntity<Map<String, String>> handleDualSignoff(DualSignoffException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "DUAL_SIGNOFF_VIOLATION", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_STATE", "message", ex.getMessage(), "status", "409"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }
}
