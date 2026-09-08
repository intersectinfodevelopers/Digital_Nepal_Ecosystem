package np.gov.digital.platformgateway.service;

import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.dto.ConsentConfirmRequest;
import np.gov.digital.platformgateway.dto.ConsentInitiateRequest;
import np.gov.digital.platformgateway.entity.GatewayConsent;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.enums.ConsentStatus;
import np.gov.digital.platformgateway.exception.InvalidOtpException;
import np.gov.digital.platformgateway.repository.GatewayConsentRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import np.gov.digital.platformidcard.service.SparrowSmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayConsentServiceTest {

    @Mock private GatewayConsentRepository gatewayConsentRepository;
    @Mock private RelyingPartyRepository relyingPartyRepository;
    @Mock private CitizenRepository citizenRepository;
    @Mock private NidEncryptionUtil nidEncryptionUtil;
    @Mock private SparrowSmsService sparrowSmsService;
    @Mock private AuditLogService auditLogService;

    private GatewayConsentService service;
    private static final String RAW_SECRET = "correct-secret";
    private RelyingParty relyingParty;
    private Citizen citizen;
    private UUID citizenId;

    @BeforeEach
    void setUp() {
        RelyingPartyAuthenticator authenticator = new RelyingPartyAuthenticator(relyingPartyRepository);
        service = new GatewayConsentService(
                gatewayConsentRepository, citizenRepository, nidEncryptionUtil, sparrowSmsService,
                auditLogService, authenticator);

        relyingParty = RelyingParty.builder()
                .id(UUID.randomUUID())
                .name("Test Bank")
                .clientId("rp-test")
                .clientSecretHash(new BCryptPasswordEncoder().encode(RAW_SECRET))
                .build();

        citizenId = UUID.randomUUID();
        citizen = new Citizen();
        citizen.setId(citizenId);
        citizen.setPhoneEnc("enc-phone");

        lenient().when(relyingPartyRepository.findByClientId("rp-test")).thenReturn(Optional.of(relyingParty));
        lenient().when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        lenient().when(gatewayConsentRepository.save(any())).thenAnswer(inv -> {
            GatewayConsent consent = inv.getArgument(0);
            if (consent.getId() == null) consent.setId(UUID.randomUUID());
            return consent;
        });
        lenient().when(nidEncryptionUtil.decrypt("enc-phone")).thenReturn("9841000000");
    }

    @Test
    void initiate_sendsOtpBySmsAndStoresPendingConsent() {
        UUID consentId = service.initiate("rp-test", RAW_SECRET,
                ConsentInitiateRequest.builder().citizenId(citizenId).purposeCode("KYC").build());

        assertThat(consentId).isNotNull();
        verify(sparrowSmsService).sendSms(eq("9841000000"), contains("consent code"));

        var captor = org.mockito.ArgumentCaptor.forClass(GatewayConsent.class);
        verify(gatewayConsentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ConsentStatus.PENDING);
        assertThat(captor.getValue().getOtpCodeHash()).isNotBlank();
    }

    @Test
    void initiate_citizenWithNoPhone_doesNotCallSmsService() {
        citizen.setPhoneEnc(null);

        service.initiate("rp-test", RAW_SECRET,
                ConsentInitiateRequest.builder().citizenId(citizenId).purposeCode("KYC").build());

        verifyNoInteractions(sparrowSmsService);
    }

    @Test
    void confirm_correctOtp_marksConfirmed() {
        String otp = captureGeneratedOtp();

        GatewayConsent pending = GatewayConsent.builder()
                .citizen(citizen).relyingPartyId(relyingParty.getId()).purposeCode("KYC")
                .otpCodeHash(new BCryptPasswordEncoder().encode(otp))
                .otpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .status(ConsentStatus.PENDING).build();
        when(gatewayConsentRepository.findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                citizenId, relyingParty.getId(), "KYC", ConsentStatus.PENDING)).thenReturn(Optional.of(pending));

        service.confirm("rp-test", RAW_SECRET,
                ConsentConfirmRequest.builder().citizenId(citizenId).purposeCode("KYC").otpCode(otp).build());

        assertThat(pending.getStatus()).isEqualTo(ConsentStatus.CONFIRMED);
        assertThat(pending.getConfirmedAt()).isNotNull();
        verify(auditLogService).log(any(), eq(citizenId), anyString());
    }

    @Test
    void confirm_wrongOtp_throwsInvalidOtp() {
        GatewayConsent pending = GatewayConsent.builder()
                .citizen(citizen).relyingPartyId(relyingParty.getId()).purposeCode("KYC")
                .otpCodeHash(new BCryptPasswordEncoder().encode("999999"))
                .otpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .status(ConsentStatus.PENDING).build();
        when(gatewayConsentRepository.findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                citizenId, relyingParty.getId(), "KYC", ConsentStatus.PENDING)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm("rp-test", RAW_SECRET,
                ConsentConfirmRequest.builder().citizenId(citizenId).purposeCode("KYC").otpCode("000000").build()))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void confirm_expiredOtp_marksExpiredAndThrows() {
        GatewayConsent pending = GatewayConsent.builder()
                .citizen(citizen).relyingPartyId(relyingParty.getId()).purposeCode("KYC")
                .otpCodeHash(new BCryptPasswordEncoder().encode("123456"))
                .otpExpiresAt(Instant.now().minusSeconds(60))
                .status(ConsentStatus.PENDING).build();
        when(gatewayConsentRepository.findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                citizenId, relyingParty.getId(), "KYC", ConsentStatus.PENDING)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm("rp-test", RAW_SECRET,
                ConsentConfirmRequest.builder().citizenId(citizenId).purposeCode("KYC").otpCode("123456").build()))
                .isInstanceOf(InvalidOtpException.class);
        assertThat(pending.getStatus()).isEqualTo(ConsentStatus.EXPIRED);
    }

    @Test
    void confirm_noPendingConsent_throwsInvalidOtp() {
        when(gatewayConsentRepository.findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                citizenId, relyingParty.getId(), "KYC", ConsentStatus.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("rp-test", RAW_SECRET,
                ConsentConfirmRequest.builder().citizenId(citizenId).purposeCode("KYC").otpCode("123456").build()))
                .isInstanceOf(InvalidOtpException.class);
    }

    /** Captures the OTP GatewayConsentService generated during initiate() via the SMS message text. */
    private String captureGeneratedOtp() {
        service.initiate("rp-test", RAW_SECRET,
                ConsentInitiateRequest.builder().citizenId(citizenId).purposeCode("KYC").build());
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sparrowSmsService).sendSms(anyString(), captor.capture());
        String message = captor.getValue();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("code is (\\d{6})").matcher(message);
        assertThat(matcher.find()).isTrue();
        reset(sparrowSmsService, gatewayConsentRepository);
        lenient().when(gatewayConsentRepository.save(any())).thenAnswer(inv -> {
            GatewayConsent consent = inv.getArgument(0);
            if (consent.getId() == null) consent.setId(UUID.randomUUID());
            return consent;
        });
        return matcher.group(1);
    }
}
