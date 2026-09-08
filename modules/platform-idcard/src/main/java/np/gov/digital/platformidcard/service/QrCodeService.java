package np.gov.digital.platformidcard.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * QrCodeService
 * Generates HMAC-SHA256 signed QR codes for ID cards.
 */
@Slf4j
@Service
public class QrCodeService {

    // SECURITY FIX: had a default of "default-secret-change-in-prod" —
    // the exact same class of bug as the old ENCRYPTION_KEY/PEPPER_SECRET
    // defaults fixed elsewhere in this codebase (see NidEncryptionUtil's
    // constructor validation comment). Anyone with repo access already
    // knows this literal string, so any deployment that forgot to set
    // idcard.qr.secret would sign every ID card's QR token with a secret
    // that provides no actual integrity guarantee — a forged card would
    // verify as VALID. No default: fails to start instead.
    @Value("${idcard.qr.secret}")
    private String qrSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final int QR_WIDTH  = 200;
    private static final int QR_HEIGHT = 200;

    public byte[] generateQrCode(String citizenId,
                                 String cardType,
                                 String issuedDate) throws WriterException, IOException {
        String token     = buildSignedToken(citizenId, cardType, issuedDate);
        String verifyUrl = baseUrl + "/api/v1/idcards/verify/" + token;

        log.debug("QrCodeService: generating QR for citizen={} cardType={}", citizenId, cardType);

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer    = new QRCodeWriter();
        BitMatrix    bitMatrix = writer.encode(
                verifyUrl, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT, hints);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        return outputStream.toByteArray();
    }

    public String buildSignedToken(String citizenId,
                                   String cardType,
                                   String issuedDate) {
        String payload = citizenId + "|" + cardType + "|" + issuedDate;
        String hmac    = computeHmac(payload);
        String full    = payload + "|" + hmac;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(full.getBytes(StandardCharsets.UTF_8));
    }

    public VerifyResult verifyToken(String token) {
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);

            String[] parts = decoded.split("\\|");
            if (parts.length != 4) {
                return VerifyResult.invalid("MALFORMED_TOKEN");
            }

            String citizenId  = parts[0];
            String cardType   = parts[1];
            String issuedDate = parts[2];
            String signature  = parts[3];

            String payload      = citizenId + "|" + cardType + "|" + issuedDate;
            String expectedHmac = computeHmac(payload);

            if (!expectedHmac.equals(signature)) {
                log.warn("QrCodeService: SIGNATURE_MISMATCH for token={}", token);
                return VerifyResult.invalid("SIGNATURE_MISMATCH");
            }

            return VerifyResult.valid(citizenId, cardType, issuedDate);

        } catch (Exception e) {
            log.error("QrCodeService: error verifying token: {}", e.getMessage());
            return VerifyResult.invalid("INVALID_TOKEN");
        }
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("QrCodeService: HMAC computation failed", e);
        }
    }

    public record VerifyResult(
            boolean valid,
            String  citizenId,
            String  cardType,
            String  issuedDate,
            String  reason
    ) {
        static VerifyResult valid(String citizenId, String cardType, String issuedDate) {
            return new VerifyResult(true, citizenId, cardType, issuedDate, null);
        }
        static VerifyResult invalid(String reason) {
            return new VerifyResult(false, null, null, null, reason);
        }
    }
}
