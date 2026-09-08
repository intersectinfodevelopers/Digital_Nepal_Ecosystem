package np.gov.digital.platformgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.dto.VerifyRequest;
import np.gov.digital.platformgateway.entity.CitizenRelyingPartyToken;
import np.gov.digital.platformgateway.entity.GatewayConsent;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.entity.RelyingPartyPurpose;
import np.gov.digital.platformgateway.enums.ConsentStatus;
import np.gov.digital.platformgateway.enums.PurposeStatus;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.exception.ConsentRequiredException;
import np.gov.digital.platformgateway.exception.RelyingPartySuspendedException;
import np.gov.digital.platformgateway.exception.ScopeDeniedException;
import np.gov.digital.platformgateway.repository.CitizenRelyingPartyTokenRepository;
import np.gov.digital.platformgateway.repository.GatewayAccessLogRepository;
import np.gov.digital.platformgateway.repository.GatewayConsentRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyPurposeRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayVerificationServiceTest {

    @Mock private RelyingPartyRepository relyingPartyRepository;
    @Mock private RelyingPartyPurposeRepository relyingPartyPurposeRepository;
    @Mock private CitizenRelyingPartyTokenRepository tokenRepository;
    @Mock private GatewayConsentRepository gatewayConsentRepository;
    @Mock private GatewayAccessLogRepository gatewayAccessLogRepository;
    @Mock private CitizenRepository citizenRepository;
    @Mock private NidEncryptionUtil nidEncryptionUtil;
    @Mock private AuditLogService auditLogService;

    private GatewayVerificationService service;

    private static final String RAW_SECRET = "correct-secret";
    private RelyingParty relyingParty;
    private Citizen citizen;
    private UUID citizenId;

    @BeforeEach
    void setUp() {
        RelyingPartyAuthenticator authenticator = new RelyingPartyAuthenticator(relyingPartyRepository);
        // A real GatewayDenialRecorder, not a mock: it owns the anomaly
        // ladder now (see its class Javadoc for why), and this test wires
        // it to the same mocked repositories so denial-path assertions
        // below (consecutiveDeniedCount, purpose/party auto-suspension)
        // still observe the real mutation logic, not a stub standing in
        // for it. Its @Transactional(REQUIRES_NEW) is a Spring-proxy
        // concern that doesn't apply when constructed directly like this.
        GatewayDenialRecorder denialRecorder = new GatewayDenialRecorder(
                citizenRepository, relyingPartyRepository, relyingPartyPurposeRepository,
                gatewayAccessLogRepository, auditLogService);
        service = new GatewayVerificationService(
                relyingPartyRepository, relyingPartyPurposeRepository, tokenRepository,
                gatewayConsentRepository, gatewayAccessLogRepository, citizenRepository,
                nidEncryptionUtil, auditLogService, new ObjectMapper(), authenticator, denialRecorder);

        relyingParty = RelyingParty.builder()
                .id(UUID.randomUUID())
                .name("Test Bank")
                .clientId("rp-test")
                .clientSecretHash(new BCryptPasswordEncoder().encode(RAW_SECRET))
                .status(RelyingPartyStatus.ACTIVE)
                .consecutiveDeniedCount(0)
                .build();

        citizenId = UUID.randomUUID();
        Ward ward = new Ward();
        ward.setId(UUID.randomUUID());
        citizen = new Citizen();
        citizen.setId(citizenId);
        citizen.setWard(ward);
        citizen.setNameEn("Ram Bahadur");

        lenient().when(relyingPartyRepository.findByClientId("rp-test")).thenReturn(Optional.of(relyingParty));
        lenient().when(relyingPartyRepository.findById(relyingParty.getId())).thenReturn(Optional.of(relyingParty));
        lenient().when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        lenient().when(relyingPartyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(gatewayAccessLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tokenRepository.save(any(CitizenRelyingPartyToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private VerifyRequest request() {
        return VerifyRequest.builder().citizenId(citizenId).build();
    }

    @Test
    void verify_wrongClientSecret_throwsInvalidCredentials() {
        assertThatThrownBy(() -> service.verify("rp-test", "wrong-secret", "KYC", request()))
                .isInstanceOf(np.gov.digital.platformgateway.exception.InvalidClientCredentialsException.class);
    }

    @Test
    void verify_unknownPurpose_throwsScopeDenied() {
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("rp-test", RAW_SECRET, "KYC", request()))
                .isInstanceOf(ScopeDeniedException.class);
        verify(gatewayAccessLogRepository).save(any());
    }

    @Test
    void verify_suspendedRelyingParty_throwsSuspended() {
        relyingParty.setStatus(RelyingPartyStatus.SUSPENDED);

        assertThatThrownBy(() -> service.verify("rp-test", RAW_SECRET, "KYC", request()))
                .isInstanceOf(RelyingPartySuspendedException.class);
    }

    @Test
    void verify_lapsedCertificate_throwsSuspendedEvenIfStatusStillActive() {
        relyingParty.setCertificateExpiresAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.verify("rp-test", RAW_SECRET, "KYC", request()))
                .isInstanceOf(RelyingPartySuspendedException.class);
    }

    @Test
    void verify_consentRequiredButNotConfirmed_throwsConsentRequired() {
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .relyingParty(relyingParty).purposeCode("KYC")
                .allowedFields("[\"nameEn\"]").requiresConsent(true).status(PurposeStatus.ACTIVE).build();
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.of(purpose));
        when(gatewayConsentRepository.findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                citizenId, relyingParty.getId(), "KYC", ConsentStatus.CONFIRMED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("rp-test", RAW_SECRET, "KYC", request()))
                .isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void verify_withConfirmedConsent_succeedsAndReturnsOnlyWhitelistedFields() {
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .relyingParty(relyingParty).purposeCode("KYC")
                .allowedFields("[\"nameEn\"]").requiresConsent(true).status(PurposeStatus.ACTIVE).build();
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.of(purpose));
        when(gatewayConsentRepository.findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                citizenId, relyingParty.getId(), "KYC", ConsentStatus.CONFIRMED))
                .thenReturn(Optional.of(GatewayConsent.builder().status(ConsentStatus.CONFIRMED).build()));
        when(tokenRepository.findByCitizen_IdAndRelyingPartyId(citizenId, relyingParty.getId()))
                .thenReturn(Optional.empty());

        var result = service.verify("rp-test", RAW_SECRET, "KYC", request());

        assertThat(result.getFields()).containsOnlyKeys("nameEn");
        assertThat(result.getFields().get("nameEn")).isEqualTo("Ram Bahadur");
        assertThat(result.getPairwiseToken()).isNotNull();
    }

    @Test
    void verify_legalMandateNoConsent_succeedsWithoutAnyConsentRow() {
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .relyingParty(relyingParty).purposeCode("KYC")
                .allowedFields("[\"nameEn\"]").requiresConsent(false).status(PurposeStatus.ACTIVE).build();
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.of(purpose));
        when(tokenRepository.findByCitizen_IdAndRelyingPartyId(citizenId, relyingParty.getId()))
                .thenReturn(Optional.empty());

        var result = service.verify("rp-test", RAW_SECRET, "KYC", request());

        assertThat(result.getFields()).containsKey("nameEn");
        verifyNoInteractions(gatewayConsentRepository);
    }

    @Test
    void verify_reusesExistingPairwiseTokenAcrossCalls() {
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .relyingParty(relyingParty).purposeCode("KYC")
                .allowedFields("[\"nameEn\"]").requiresConsent(false).status(PurposeStatus.ACTIVE).build();
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.of(purpose));
        UUID existingToken = UUID.randomUUID();
        when(tokenRepository.findByCitizen_IdAndRelyingPartyId(citizenId, relyingParty.getId()))
                .thenReturn(Optional.of(CitizenRelyingPartyToken.builder().token(existingToken).build()));

        var result = service.verify("rp-test", RAW_SECRET, "KYC", request());

        assertThat(result.getPairwiseToken()).isEqualTo(existingToken);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void verify_repeatedScopeDenials_autoSuspendsPurposeAfterFiveConsecutiveDenials() {
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify("rp-test", RAW_SECRET, "KYC", request()))
                    .isInstanceOf(ScopeDeniedException.class);
        }

        assertThat(relyingParty.getConsecutiveDeniedCount()).isEqualTo(5);
    }

    @Test
    void verify_repeatedDenials_autoSuspendsRelyingPartyAfterTenConsecutiveDenials() {
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.empty());

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> service.verify("rp-test", RAW_SECRET, "KYC", request()));
        }

        assertThat(relyingParty.getStatus()).isEqualTo(RelyingPartyStatus.SUSPENDED);
    }

    @Test
    void verify_scopeDenied_stillPersistsAccessLogAndAuditEntry() {
        // Regression test for the bug found live: verify() is
        // @Transactional and every denial path used to throw from within
        // that same method, so Spring's rollback-on-exception undid the
        // access-log write and the audit event the instant the exception
        // left the method — neither ever actually reached the database.
        // GatewayDenialRecorder's REQUIRES_NEW is what makes both of
        // these mock verifications meaningful now.
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("rp-test", RAW_SECRET, "KYC", request()))
                .isInstanceOf(ScopeDeniedException.class);

        verify(gatewayAccessLogRepository).save(any());
        verify(auditLogService).log(any(), eq(citizenId), anyString());
    }

    @Test
    void verify_successResetsConsecutiveDeniedCount() {
        relyingParty.setConsecutiveDeniedCount(3);
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .relyingParty(relyingParty).purposeCode("KYC")
                .allowedFields("[\"nameEn\"]").requiresConsent(false).status(PurposeStatus.ACTIVE).build();
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), "KYC"))
                .thenReturn(Optional.of(purpose));
        when(tokenRepository.findByCitizen_IdAndRelyingPartyId(citizenId, relyingParty.getId()))
                .thenReturn(Optional.empty());

        service.verify("rp-test", RAW_SECRET, "KYC", request());

        assertThat(relyingParty.getConsecutiveDeniedCount()).isEqualTo(0);
    }
}
