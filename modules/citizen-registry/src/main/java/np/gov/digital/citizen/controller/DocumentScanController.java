package np.gov.digital.citizen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.NidScanResult;
import np.gov.digital.citizen.service.NidDocumentScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Tag(name = "Document Scanning", description = "OCR-assisted pre-fill from a photo of a citizenship certificate / national ID")
@RestController
@RequestMapping("/v1/citizens/documents")
@RequiredArgsConstructor
public class DocumentScanController {

    private final NidDocumentScanService documentScanService;

    @Operation(
            summary = "Scan a citizenship certificate / national ID photo",
            description = "Runs OCR against the uploaded image and returns candidate name/DOB/citizenship-"
                    + "number/sex values to pre-fill the registration form. This is a SUGGESTION only — "
                    + "nothing here is saved, and the Ward Admin must confirm every field before submitting "
                    + "the actual registration. Requires WARD_ADMIN or LOCAL_BODY_ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan completed — check fieldsNotDetected for anything OCR missed"),
            @ApiResponse(responseCode = "400", description = "Uploaded file is not a readable image"),
            @ApiResponse(responseCode = "500", description = "OCR engine failed (e.g. Nepali language data not installed)")
    })
    @PreAuthorize("hasAnyRole('WARD_ADMIN', 'LOCAL_BODY_ADMIN')")
    @PostMapping(value = "/scan", consumes = "multipart/form-data")
    public ResponseEntity<NidScanResult> scan(@RequestParam("file") MultipartFile file) {
        log.info("DocumentScanController: scanning uploaded document, size={} bytes", file.getSize());
        return ResponseEntity.ok(documentScanService.scan(file));
    }
}
