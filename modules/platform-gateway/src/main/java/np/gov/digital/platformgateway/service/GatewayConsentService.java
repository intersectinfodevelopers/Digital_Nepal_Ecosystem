package np.gov.digital.platformgateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.dto.ConsentConfirmRequest;
import np.gov.digital.platformgateway.dto.ConsentInitiateRequest;
import np.gov.digital.platformgateway.entity.GatewayConsent;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.enums.ConsentStatus;
import np.gov.digital.platformgateway.exception.InvalidOtpException;
import np.gov.digital.platformgateway.repository.GatewayConsentRepository;
import np.gov.digital.platformidcard.service.SparrowSmsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Commercial-purpose OTP consent (Extended Modules §6.4) — the citizen
 * confirms, via a one-time code sent to their own registered phone, that
 * this specific relying party may verify this specific purpose. Reuses
 * SparrowSmsService (already built for platform-idcard) rather than
 * standing up a second SMS integration.
 *
 * Both initiate and confirm are relying-party-facing calls, authenticated
 * the same clientId/clientSecret way as verify() — there's no separate
 * citizen-facing channel in this backend for the citizen to enter the
 * OTP directly, so the relying party is trusted to relay it back
 * (their own counter, app, or IVR flow) exactly as the design doc's
 * OTP-consent-via-relying-party pattern assumes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayConsentService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MINUTES = 10;

    private final GatewayConsentRepository gatewayConsentRepository;
    private final CitizenRepository citizenRepository;
    private final NidEncryptionUtil nidEncryptionUtil;
    private final SparrowSmsService sparrowSmsService;
    private final AuditLogService auditLogService;
    private final RelyingPartyAuthenticator relyingPartyAuthenticator;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public UUID initiate(String clientId, String clientSecret, ConsentInitiateRequest request) {
        RelyingParty relyingParty = relyingPartyAuthenticator.authenticate(clientId, clientSecret);
        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getCitizenId()));

        String otp = generateOtp();

        GatewayConsent consent = GatewayConsent.builder()
                .citizen(citizen)
                .relyingPartyId(relyingParty.getId())
                .purposeCode(request.getPurposeCode())
                .otpCodeHash(passwordEncoder.encode(otp))
                .otpExpiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES))
                .status(ConsentStatus.PENDING)
                .build();
        GatewayConsent saved = gatewayConsentRepository.save(consent);

        String phone = citizen.getPhoneEnc() != null ? nidEncryptionUtil.decrypt(citizen.getPhoneEnc()) : null;
        if (phone != null && !phone.isBlank()) {
            sparrowSmsService.sendSms(phone,
                    "Digital Nepal: " + relyingParty.getName() + " is requesting your " + request.getPurposeCode()
                            + " information. Your consent code is " + otp + ". Valid for " + OTP_TTL_MINUTES + " minutes.");
        } else {
            log.warn("GatewayConsentService: citizen {} has no registered phone number — OTP {} could not be delivered by SMS",
                    citizen.getId(), saved.getId());
        }

        log.info("Gateway consent OTP initiated — citizen: {}, relyingParty: {}, purpose: {}, consentId: {}",
                citizen.getId(), relyingParty.getId(), request.getPurposeCode(), saved.getId());
        return saved.getId();
    }

    @Transactional
    public void confirm(String clientId, String clientSecret, ConsentConfirmRequest request) {
        RelyingParty relyingParty = relyingPartyAuthenticator.authenticate(clientId, clientSecret);

        GatewayConsent consent = gatewayConsentRepository
                .findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                        request.getCitizenId(), relyingParty.getId(), request.getPurposeCode(), ConsentStatus.PENDING)
                .orElseThrow(() -> new InvalidOtpException(
                        "No pending consent request for this citizen/purpose — call consent/initiate first."));

        if (consent.getOtpExpiresAt().isBefore(Instant.now())) {
            consent.setStatus(ConsentStatus.EXPIRED);
            gatewayConsentRepository.save(consent);
            throw new InvalidOtpException("This OTP has expired — initiate consent again.");
        }
        if (!passwordEncoder.matches(request.getOtpCode(), consent.getOtpCodeHash())) {
            throw new InvalidOtpException("Incorrect OTP.");
        }

        consent.setStatus(ConsentStatus.CONFIRMED);
        consent.setConfirmedAt(Instant.now());
        gatewayConsentRepository.save(consent);

        auditLogService.log(AuditEventType.GATEWAY_CONSENT_CONFIRMED, consent.getCitizen().getId(),
                "Citizen confirmed gateway consent for purpose " + consent.getPurposeCode()
                        + " requested by " + relyingParty.getName());
        log.info("Gateway consent CONFIRMED — consentId: {}, citizen: {}", consent.getId(), consent.getCitizen().getId());
    }

    private String generateOtp() {
        int otp = secureRandom.nextInt(1_000_000);
        return String.format("%0" + OTP_LENGTH + "d", otp);
    }
}
