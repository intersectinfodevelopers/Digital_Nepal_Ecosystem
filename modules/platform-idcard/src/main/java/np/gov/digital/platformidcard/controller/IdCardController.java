package np.gov.digital.platformidcard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformidcard.dto.IdCardInitiateRequest;
import np.gov.digital.platformidcard.dto.IdCardVerifyResponse;
import np.gov.digital.platformidcard.entity.OfficialDocument;
import np.gov.digital.platformidcard.enums.DocumentType;
import np.gov.digital.platformidcard.exception.InvalidDocumentStateException;
import np.gov.digital.platformidcard.exception.OfficialDocumentNotFoundException;
import np.gov.digital.platformidcard.service.IdCardPdfGenerator;
import np.gov.digital.platformidcard.service.OfficialDocumentService;
import np.gov.digital.platformidcard.service.QrCodeService;
import np.gov.digital.platformidcard.service.SparrowSmsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Tag(name = "ID Cards", description = "Disability/Unemployment ID card initiation, approval/PDF issuance, and QR verification — backed by official_document (SDD Extended Modules §4.7)")
@RestController
@RequestMapping("/v1/idcards")
@RequiredArgsConstructor
public class IdCardController {

    private final OfficialDocumentService officialDocumentService;
    private final IdCardPdfGenerator      pdfGenerator;
    private final QrCodeService           qrCodeService;
    private final SparrowSmsService       smsService;
    private final NidEncryptionUtil       nidEncryptionUtil;

    @Operation(
            summary = "Initiate an ID card request",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Creates a PRINT_PENDING "
                    + "official_document row, pending LOCAL_BODY_ADMIN approval. cardType must be "
                    + "DISABILITY or UNEMPLOYMENT (bare — no _CARD suffix; that's an internal detail "
                    + "of DocumentType, kept out of this request shape for backward compatibility with "
                    + "the pre-existing contract).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Initiation submitted, status PRINT_PENDING"),
            @ApiResponse(responseCode = "400", description = "cardType is not a valid ID card type"),
            @ApiResponse(responseCode = "404", description = "Citizen does not exist")
    })
    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<Map<String, UUID>> initiate(
            @RequestBody IdCardInitiateRequest request, Authentication authentication) {

        DocumentType documentType = parseCardOnlyType(request.cardType());
        log.info("IdCardController: initiate documentType={} for citizen={}",
                documentType, request.citizenId());

        OfficialDocument document = officialDocumentService.initiate(
                documentType, request.citizenId(), actorId(authentication));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("documentId", document.getId()));
    }

    @Operation(
            summary = "Approve an ID card and generate the printable PDF",
            description = "Requires LOCAL_BODY_ADMIN role. Generates the ID card PDF from the citizen's "
                    + "real registered details, sets a 3-year expiry, and SMS-notifies the citizen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "404", description = "No document with this ID"),
            @ApiResponse(responseCode = "409", description = "Document is not PRINT_PENDING"),
            @ApiResponse(responseCode = "500", description = "PDF generation failed")
    })
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<byte[]> approve(
            @Parameter(description = "official_document ID") @PathVariable("id") UUID id,
            Authentication authentication) {
        log.info("IdCardController: approving documentId={}", id);

        OfficialDocument document = officialDocumentService.approveAndIssue(id, actorId(authentication));
        Citizen citizen = document.getCitizen();

        try {
            String nameNp = citizen.getNameNp();
            String nameEn = citizen.getNameEn();
            String citizenshipNo = citizen.getCitizenshipNoNorm() != null ? citizen.getCitizenshipNoNorm() : "N/A";
            String wardNumber = String.valueOf(citizen.getWard().getWardNo());
            String issuedDate = DateTimeFormatter.ISO_LOCAL_DATE.format(
                    document.getIssuedAt().atZone(java.time.ZoneOffset.UTC));
            String expiryDate = DateTimeFormatter.ISO_LOCAL_DATE.format(
                    document.getExpiresAt().atZone(java.time.ZoneOffset.UTC));

            byte[] pdfBytes = pdfGenerator.generateIdCard(
                    citizen.getId().toString(), nameNp, nameEn, citizenshipNo,
                    document.getDocumentType().name(), wardNumber, issuedDate, expiryDate
            );

            if (citizen.getPhoneEnc() != null) {
                String phone = nidEncryptionUtil.decrypt(citizen.getPhoneEnc());
                smsService.sendIdCardReadyNotification(phone, document.getDocumentType().name(), wardNumber);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "id_card_" + citizen.getId() + ".pdf");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            log.error("IdCardController: PDF generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
            summary = "Revoke an issued ID card",
            description = "Requires LOCAL_BODY_ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Revoked"),
            @ApiResponse(responseCode = "404", description = "No document with this ID"),
            @ApiResponse(responseCode = "409", description = "Document is not currently ISSUED")
    })
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<Void> revoke(
            @Parameter(description = "official_document ID") @PathVariable("id") UUID id,
            @RequestParam String reason,
            Authentication authentication) {
        officialDocumentService.revoke(id, actorId(authentication), reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Verify an ID card via its QR token",
            description = "Public endpoint — no authentication required. Returns card validity and "
                    + "type only; no citizen PII beyond name and ward. Checks both the token's "
                    + "cryptographic signature and the document's current status — a revoked or "
                    + "expired document now correctly reports invalid even with a validly signed token.")
    @SecurityRequirements
    @GetMapping("/verify/{token}")
    public ResponseEntity<IdCardVerifyResponse> verify(
            @Parameter(description = "QR-encoded verification token") @PathVariable("token") String token) {
        log.info("IdCardController: verifying QR token");

        OfficialDocumentService.VerifyResult result = officialDocumentService.verify(token);

        if (!result.valid()) {
            return ResponseEntity.ok(new IdCardVerifyResponse(
                    "INVALID", null, null, null, null, result.reason()
            ));
        }

        OfficialDocument document = result.document();
        Citizen citizen = document.getCitizen();
        return ResponseEntity.ok(new IdCardVerifyResponse(
                "VALID",
                citizen.getNameEn(),
                document.getDocumentType().name(),
                DateTimeFormatter.ISO_LOCAL_DATE.format(document.getIssuedAt().atZone(java.time.ZoneOffset.UTC)),
                "Ward " + citizen.getWard().getWardNo(),
                null
        ));
    }

    private DocumentType parseCardOnlyType(String cardType) {
        DocumentType type;
        try {
            type = DocumentType.valueOf(cardType + "_CARD");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "cardType must be DISABILITY or UNEMPLOYMENT, got: " + cardType);
        }
        if (type.isCertificate()) {
            throw new IllegalArgumentException("cardType must be DISABILITY or UNEMPLOYMENT, got: " + cardType);
        }
        return type;
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

    @ExceptionHandler(CitizenNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCitizenNotFound(CitizenNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CITIZEN_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(OfficialDocumentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleDocumentNotFound(OfficialDocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "DOCUMENT_NOT_FOUND", "message", ex.getMessage(), "status", "404"));
    }

    @ExceptionHandler(InvalidDocumentStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidState(InvalidDocumentStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_DOCUMENT_STATE", "message", ex.getMessage(), "status", "409"));
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
