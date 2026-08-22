package np.gov.digital.citizen.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class NidEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKey secretKey;

    // The pepper for NID dedup HMAC (SDD Critical Implementation Note #2).
    // MUST live only in Vault/env — never hardcoded, never in the database,
    // never logged. Deliberately has NO default value: if app.pepper is
    // missing, startup fails loudly rather than silently using a weak or
    // predictable pepper.
    private final byte[] pepperBytes;

    // SECURITY FIX: the previous version of this constructor defaulted
    // app.encryption.key to a hardcoded literal
    // ("12345678901234567890123456789012") whenever the ENCRYPTION_KEY env
    // var was not set. That default was committed to source control, which
    // means every deployment that forgot to set the env var — including,
    // critically, any deployment where a developer copy-pasted this class —
    // was encrypting every citizen's NID, DOB, and phone number with a key
    // anyone with repo access already knows. There is intentionally no
    // default here anymore: a missing key must fail application startup,
    // not silently downgrade security.
    public NidEncryptionUtil(
            @Value("${app.encryption.key}") String encryptionKey,
            @Value("${app.pepper}") String pepper) {

        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.key is not set. Refusing to start with no "
                    + "encryption key rather than fall back to an insecure default. "
                    + "Set ENCRYPTION_KEY via Vault/env (see application.yml).");
        }
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "ENCRYPTION_KEY must be exactly 32 characters (256 bits). Current length: "
                            + keyBytes.length
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");

        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException(
                    "app.pepper is not set. Refusing to start — NID dedup HMAC "
                    + "requires a pepper stored outside the database (Vault/env). "
                    + "See SDD Section 3.4 / Critical Implementation Note #2.");
        }
        this.pepperBytes = pepper.getBytes(StandardCharsets.UTF_8);
    }

    // ENCRYPT
    // Encrypts plaintext NID using AES-256/GCM.
    public String encrypt(String plaintext){
        try{
            // Generate a fresh random IV for every encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext so we can extract it during decryption
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e){
            throw new RuntimeException("Failed to encrypt NID", e);
        }
    }

    // HASH — DEPRECATED. Plain SHA-256, no pepper. A NID has a small,
    // guessable format (10 digits), so a plain hash is brute-forceable
    // offline if the database is ever dumped or leaked. Kept only so
    // existing rows / the backfill migration (V16) have something to read
    // from during the transition. Do not call this for new registrations —
    // use hmac() instead.
    @Deprecated
    public String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash NID", e);
        }
    }

    // HMAC — the correct dedup mechanism per SDD Critical Implementation
    // Note #2. HMAC-SHA256(nid, pepper): fast exact-match lookup for
    // uniqueness checks, but cannot be reversed or brute-forced without the
    // pepper, which is never stored in this database. Use this for all new
    // citizen registrations; populate citizen.nid_hmac, not nid_hash.
    public String hmac(String plaintext) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepperBytes, HMAC_ALGORITHM));
            byte[] hmacBytes = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            // Deliberately do not include `plaintext` (the raw NID) in the
            // exception message or any downstream log line.
            throw new RuntimeException("Failed to compute NID HMAC", e);
        }
    }

    // DECRYPT
    // Decrypts an AES-256/GCM encrypted NID.
    public String decrypt(String encryptedBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

            // Extract IV (first 12 bytes) and ciphertext (remainder)
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt NID", e);
        }
    }

    // CITIZENSHIP NUMBER SANITIZER
    public String normalizeCitizenshipNo(String rawCitizenshipNo) {
        if (rawCitizenshipNo == null) return null;
        return rawCitizenshipNo.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }

    // PRIVATE HELPERS
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
