package np.gov.digital.citizen.dto;

import lombok.*;

import java.util.List;

/**
 * Result of scanning an uploaded citizenship-certificate / national-ID
 * image. This is a SUGGESTION for pre-filling the registration form — it
 * is never written to the database directly. A human (the Ward Admin doing
 * the registration) must review and confirm every field before it becomes
 * part of {@link CitizenRegistrationRequest}. OCR on a legal identity
 * document is not reliable enough to trust unattended, and Nepal's
 * Individual Privacy Act requires the citizen's own attested data anyway,
 * not a machine's best guess at it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NidScanResult {

    private String nameEn;
    private String nameNp;

    /** As printed on the document, B.S. calendar, e.g. "2054-05-12". */
    private String dobBs;

    /** {@link #dobBs} converted to the Gregorian calendar, YYYY-MM-DD — null if dobBs wasn't found or didn't parse as a valid B.S. date. */
    private String dobAd;

    private String citizenshipNo;

    /** MALE / FEMALE / OTHER, or null if not confidently detected. */
    private String sex;

    private String fatherName;
    private String motherName;
    private String district;

    /** Field names that were not found and need manual entry — always check this before trusting an absence. */
    private List<String> fieldsNotDetected;

    /** Raw OCR output, for a human to cross-check against the fields extracted above. */
    private String rawText;

    /** Language mode Tesseract ran in (e.g. "eng+nep"), for debugging poor extractions. */
    private String ocrLanguage;
}
