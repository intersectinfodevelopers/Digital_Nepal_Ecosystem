package np.gov.digital.citizen.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import np.gov.digital.citizen.dto.NidScanResult;
import np.gov.digital.citizen.util.BikramSambatConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts candidate registration fields from a photo of a Nepali
 * citizenship certificate / national ID, via Tesseract OCR.
 *
 * This is deliberately a SUGGESTION engine, not an authority: see
 * {@link NidScanResult}'s javadoc. Field extraction is regex-based against
 * the standard bilingual certificate layout (English label lines are far
 * more OCR-reliable than Devanagari, so English labels are matched first
 * with Nepali labels as fallback) — it will miss fields on a poor-quality
 * photo, a non-standard document, or an old certificate format, which is
 * exactly why every field comes back editable rather than pre-committed.
 */
@Service
@Slf4j
public class NidDocumentScanService {

    private final BikramSambatConverter bsConverter;
    private final String tessDataPath;

    public NidDocumentScanService(
            BikramSambatConverter bsConverter,
            @Value("${ocr.tessdata-path:/usr/share/tesseract-ocr/5/tessdata}") String tessDataPath) {
        this.bsConverter = bsConverter;
        this.tessDataPath = tessDataPath;
    }

    // Citizenship certificates print the number as district-year-serial,
    // e.g. "23-01-77-04321"; formats vary in segment count, so this matches
    // 2-6 digit groups joined by '-' or '/', at least 3 groups.
    private static final Pattern CITIZENSHIP_NO = Pattern.compile(
            "(?:citizenship|certificate)[^0-9]{0,20}(?:no\\.?|number)?[:\\s]*"
                    + "((?:\\d{1,6}[-/]){2,5}\\d{1,6})",
            Pattern.CASE_INSENSITIVE);

    // B.S. dates on certificates are printed YYYY-MM-DD or YYYY/MM/DD, with
    // a 4-digit year in the 1970-2090 B.S. range this system supports.
    private static final Pattern DOB_LABEL_LINE = Pattern.compile(
            "(?:date\\s*of\\s*birth|dob|जन्म\\s*मिति)[^0-9]{0,15}"
                    + "((?:19[7-9]\\d|20[0-9]\\d)[-/.](?:0?[1-9]|1[0-2])[-/.](?:0?[1-9]|[12]\\d|3[0-2]))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_LABEL_LINE = Pattern.compile(
            "(?:full\\s*name|name)[:\\s]+([A-Za-z][A-Za-z .]{2,60})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FATHER_LABEL_LINE = Pattern.compile(
            "father'?s?\\s*name[:\\s]+([A-Za-z][A-Za-z .]{2,60})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MOTHER_LABEL_LINE = Pattern.compile(
            "mother'?s?\\s*name[:\\s]+([A-Za-z][A-Za-z .]{2,60})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DISTRICT_LABEL_LINE = Pattern.compile(
            "district[:\\s]+([A-Za-z][A-Za-z .]{2,40})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SEX_LINE = Pattern.compile(
            "\\b(male|female|पुरुष|महिला)\\b", Pattern.CASE_INSENSITIVE);

    public NidScanResult scan(MultipartFile image) {
        String rawText = runOcr(image);

        List<String> notDetected = new ArrayList<>();

        String nameEn = extract(NAME_LABEL_LINE, rawText, "name", notDetected);
        String fatherName = extract(FATHER_LABEL_LINE, rawText, "fatherName", notDetected);
        String motherName = extract(MOTHER_LABEL_LINE, rawText, "motherName", notDetected);
        String district = extract(DISTRICT_LABEL_LINE, rawText, "district", notDetected);
        String citizenshipNo = extract(CITIZENSHIP_NO, rawText, "citizenshipNo", notDetected);
        String sex = extractSex(rawText, notDetected);
        String dobBs = extract(DOB_LABEL_LINE, rawText, "dobBs", notDetected);
        String dobAd = convertDobIfPossible(dobBs, notDetected);

        return NidScanResult.builder()
                .nameEn(nameEn)
                .dobBs(dobBs)
                .dobAd(dobAd)
                .citizenshipNo(citizenshipNo)
                .sex(sex)
                .fatherName(fatherName)
                .motherName(motherName)
                .district(district)
                .fieldsNotDetected(notDetected)
                .rawText(rawText)
                .ocrLanguage("eng+nep")
                .build();
    }

    private String runOcr(MultipartFile image) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        // Nepali certificates mix Devanagari labels/names with Latin-script
        // numbers and, on newer formats, English transliteration — running
        // both language models together lets Tesseract pick the better
        // match per line rather than forcing one script.
        tesseract.setLanguage("eng+nep");

        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
            if (bufferedImage == null) {
                throw new IllegalArgumentException(
                        "Uploaded file is not a readable image (got content-type: "
                                + image.getContentType() + ")");
            }
            return tesseract.doOCR(bufferedImage);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded document image", e);
        } catch (TesseractException e) {
            // Most commonly: tessdata for "nep" isn't installed at
            // ocr.tessdata-path — see Dockerfile's tesseract-ocr-nep package.
            log.error("OCR failed — check that eng+nep tessdata is installed at {}", tessDataPath, e);
            throw new RuntimeException("OCR engine failed to process the document", e);
        }
    }

    private String extract(Pattern pattern, String text, String fieldName, List<String> notDetected) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        notDetected.add(fieldName);
        return null;
    }

    private String extractSex(String text, List<String> notDetected) {
        Matcher matcher = SEX_LINE.matcher(text);
        if (matcher.find()) {
            String found = matcher.group(1).toLowerCase();
            return switch (found) {
                case "male", "पुरुष" -> "MALE";
                case "female", "महिला" -> "FEMALE";
                default -> null;
            };
        }
        notDetected.add("sex");
        return null;
    }

    private String convertDobIfPossible(String dobBs, List<String> notDetected) {
        if (dobBs == null) return null;
        try {
            String[] parts = dobBs.split("[-/.]");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return bsConverter.toAd(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.warn("Detected a DOB-shaped string '{}' but it isn't a valid B.S. date: {}", dobBs, e.getMessage());
            notDetected.add("dobAd (found dobBs but could not convert — verify manually)");
            return null;
        }
    }
}
