package np.gov.digital.platformgateway.service;

import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.entity.GatewayAccessLog;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.entity.RelyingPartyPurpose;
import np.gov.digital.platformgateway.enums.AccessOutcome;
import np.gov.digital.platformgateway.enums.PurposeStatus;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.repository.GatewayAccessLogRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyPurposeRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayDenialRecorderTest {

    @Mock private CitizenRepository citizenRepository;
    @Mock private RelyingPartyRepository relyingPartyRepository;
    @Mock private RelyingPartyPurposeRepository relyingPartyPurposeRepository;
    @Mock private GatewayAccessLogRepository gatewayAccessLogRepository;
    @Mock private AuditLogService auditLogService;

    private GatewayDenialRecorder recorder;
    private UUID citizenId;
    private UUID relyingPartyId;
    private RelyingParty relyingParty;
    private Citizen citizen;

    @BeforeEach
    void setUp() {
        recorder = new GatewayDenialRecorder(
                citizenRepository, relyingPartyRepository, relyingPartyPurposeRepository,
                gatewayAccessLogRepository, auditLogService);

        citizenId = UUID.randomUUID();
        relyingPartyId = UUID.randomUUID();
        citizen = new Citizen();
        citizen.setId(citizenId);
        relyingParty = RelyingParty.builder()
                .id(relyingPartyId).name("Test Bank")
                .status(RelyingPartyStatus.ACTIVE).consecutiveDeniedCount(0).build();

        lenient().when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        lenient().when(relyingPartyRepository.findById(relyingPartyId)).thenReturn(Optional.of(relyingParty));
        lenient().when(relyingPartyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordDenial_savesAccessLogAndAuditEvent() {
        recorder.recordDenial(citizenId, relyingPartyId, "KYC", AccessOutcome.DENIED_SCOPE, null, "denied");

        var captor = org.mockito.ArgumentCaptor.forClass(GatewayAccessLog.class);
        verify(gatewayAccessLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(AccessOutcome.DENIED_SCOPE);
        verify(auditLogService).log(any(), eq(citizenId), anyString());
    }

    @Test
    void recordDenial_incrementsConsecutiveDeniedCount() {
        recorder.recordDenial(citizenId, relyingPartyId, "KYC", AccessOutcome.DENIED_SCOPE, null, "denied");

        assertThat(relyingParty.getConsecutiveDeniedCount()).isEqualTo(1);
    }

    @Test
    void recordDenial_fifthConsecutiveDenial_suspendsThePurpose() {
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .relyingParty(relyingParty).purposeCode("KYC").status(PurposeStatus.ACTIVE).build();
        when(relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingPartyId, "KYC"))
                .thenReturn(Optional.of(purpose));

        relyingParty.setConsecutiveDeniedCount(4);
        recorder.recordDenial(citizenId, relyingPartyId, "KYC", AccessOutcome.DENIED_SCOPE, null, "denied");

        assertThat(purpose.getStatus()).isEqualTo(PurposeStatus.SUSPENDED);
        verify(relyingPartyPurposeRepository).save(purpose);
    }

    @Test
    void recordDenial_tenthConsecutiveDenial_suspendsTheRelyingParty() {
        relyingParty.setConsecutiveDeniedCount(9);
        recorder.recordDenial(citizenId, relyingPartyId, "KYC", AccessOutcome.DENIED_SCOPE, null, "denied");

        assertThat(relyingParty.getStatus()).isEqualTo(RelyingPartyStatus.SUSPENDED);
        assertThat(relyingParty.getSuspensionReason()).contains("10 consecutive");
    }

    @Test
    void recordDenial_missingCitizenOrRelyingParty_doesNotThrow() {
        UUID vanishedId = UUID.randomUUID();
        when(citizenRepository.findById(vanishedId)).thenReturn(Optional.empty());

        assertThatCode(() -> recorder.recordDenial(vanishedId, relyingPartyId, "KYC", AccessOutcome.DENIED_SCOPE, null, "denied"))
                .doesNotThrowAnyException();
        verifyNoInteractions(gatewayAccessLogRepository);
    }
}
