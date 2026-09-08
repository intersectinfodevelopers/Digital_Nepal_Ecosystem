package np.gov.digital.platformgateway.job;

import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelyingPartyCertExpiryJobTest {

    @Mock private RelyingPartyRepository relyingPartyRepository;
    @Mock private AuditLogService auditLogService;

    private RelyingPartyCertExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new RelyingPartyCertExpiryJob(relyingPartyRepository, auditLogService);
    }

    @Test
    void suspendLapsedCertificates_suspendsEveryActivePartyWithLapsedCertificate() {
        RelyingParty lapsed = RelyingParty.builder()
                .status(RelyingPartyStatus.ACTIVE)
                .certificateExpiresAt(Instant.now().minusSeconds(3600))
                .build();
        when(relyingPartyRepository.findByStatusAndCertificateExpiresAtBefore(eq(RelyingPartyStatus.ACTIVE), any()))
                .thenReturn(List.of(lapsed));

        job.suspendLapsedCertificates();

        assertThat(lapsed.getStatus()).isEqualTo(RelyingPartyStatus.SUSPENDED);
        assertThat(lapsed.getSuspensionReason()).contains("certificate expired");
        verify(relyingPartyRepository).save(lapsed);
        verify(auditLogService).log(any(), isNull(), anyString());
    }

    @Test
    void suspendLapsedCertificates_noLapsedParties_doesNothing() {
        when(relyingPartyRepository.findByStatusAndCertificateExpiresAtBefore(eq(RelyingPartyStatus.ACTIVE), any()))
                .thenReturn(List.of());

        job.suspendLapsedCertificates();

        verify(relyingPartyRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void suspendLapsedCertificates_oneFailureDoesNotStopOthers() {
        RelyingParty broken = RelyingParty.builder().status(RelyingPartyStatus.ACTIVE).build();
        RelyingParty ok = RelyingParty.builder().status(RelyingPartyStatus.ACTIVE).build();
        when(relyingPartyRepository.findByStatusAndCertificateExpiresAtBefore(eq(RelyingPartyStatus.ACTIVE), any()))
                .thenReturn(List.of(broken, ok));
        doThrow(new RuntimeException("DB hiccup")).when(relyingPartyRepository).save(broken);

        job.suspendLapsedCertificates();

        verify(relyingPartyRepository).save(ok);
        assertThat(ok.getStatus()).isEqualTo(RelyingPartyStatus.SUSPENDED);
    }
}
