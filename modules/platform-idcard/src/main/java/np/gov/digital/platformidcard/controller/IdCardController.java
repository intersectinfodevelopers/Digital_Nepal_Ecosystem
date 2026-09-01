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
import np.gov.digital.platformidcard.dto.IdCardInitiateRequest;
import np.gov.digital.platformidcard.dto.IdCardVerifyResponse;
import np.gov.digital.platformidcard.service.IdCardPdfGenerator;
import np.gov.digital.platformidcard.service.QrCodeService;
import np.gov.digital.platformidcard.service.SparrowSmsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
@Slf4j
@Tag(name = "ID Cards", description = "Disability ID card initiation, approval/PDF issuance, and QR verification")
@RestController
@RequestMapping("/v1/idcards")
@RequiredArgsConstructor
public class IdCardController {

    private final IdCardPdfGenerator pdfGenerator;
    private final QrCodeService      qrCodeService;
    private final SparrowSmsService  smsService;

    @Operation(
            summary = "Initiate an ID card request",
            description = "Requires WARD_ADMIN or LOCAL_BODY_ADMIN role. Creates a request pending "
                    + "LOCAL_BODY_ADMIN approval.")
    @ApiResponse(responseCode = "201", description = "Initiation submitted, status PRINT_PENDING")
    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    public ResponseEntity<String> initiate(@RequestBody IdCardInitiateRequest request) {
        log.info("IdCardController: initiate card type={} for citizen={}",
                request.cardType(), request.citizenId());

        // TODO: save id_card record to DB with status=PRINT_PENDING
        // This will be wired to id_card table once Amit's schema is confirmed
        // For now returns 201 Created with a placeholder message

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("ID card initiation submitted. Status: PRINT_PENDING. " +
                        "Awaiting LOCAL_BODY_ADMIN approval.");
    }

    @Operation(
            summary = "Approve an ID card and generate the printable PDF",
            description = "Requires LOCAL_BODY_ADMIN role. Generates the ID card PDF and SMS-notifies the citizen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "500", description = "PDF generation failed")
    })
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('LOCAL_BODY_ADMIN')")
    public ResponseEntity<byte[]> approve(
            @Parameter(description = "ID card request ID") @PathVariable("id") String id) {
        log.info("IdCardController: approving card id={}", id);

        try {
            // TODO: load real citizen data from DB by id_card.id
            // Placeholder data for now — replace with DB lookup
            String citizenId        = id;
            String nameNp           = "नागरिक नाम";
            String nameEn           = "Citizen Name";
            String citizenshipNo    = "XX-XX-XXXXXXX";
            String cardType         = "DISABILITY";
            String wardNumber       = "4";
            String issuedDate       = LocalDate.now().toString();
            String expiryDate       = LocalDate.now().plusYears(3).toString();
            String citizenMobile    = "9841000000";

            // Generate PDF
            byte[] pdfBytes = pdfGenerator.generateIdCard(
                    citizenId, nameNp, nameEn, citizenshipNo,
                    cardType, wardNumber, issuedDate, expiryDate
            );

            // Send SMS to citizen
            smsService.sendIdCardReadyNotification(citizenMobile, cardType, wardNumber);

            // Return PDF as download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "id_card_" + citizenId + ".pdf");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            log.error("IdCardController: PDF generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
            summary = "Verify an ID card via its QR token",
            description = "Public endpoint — no authentication required. Returns card validity and "
                    + "type only; no citizen PII beyond name and ward.")
    @SecurityRequirements
    @GetMapping("/verify/{token}")
    public ResponseEntity<IdCardVerifyResponse> verify(
            @Parameter(description = "QR-encoded verification token") @PathVariable("token") String token)  {
        log.info("IdCardController: verifying QR token");

        QrCodeService.VerifyResult result = qrCodeService.verifyToken(token);

        if (!result.valid()) {
            return ResponseEntity.ok(new IdCardVerifyResponse(
                    "INVALID", null, null, null, null, result.reason()
            ));
        }

        // TODO: load name and ward from DB by citizenId
        // Returning verified data — name and card_type only, no PII
        return ResponseEntity.ok(new IdCardVerifyResponse(
                "VALID",
                "Citizen Name",      // replace with DB lookup
                result.cardType(),
                result.issuedDate(),
                "Ward 4",            // replace with DB lookup
                null
        ));
    }
}
