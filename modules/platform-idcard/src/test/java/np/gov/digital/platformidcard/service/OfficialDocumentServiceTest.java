package np.gov.digital.platformidcard.service;

import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformidcard.entity.OfficialDocument;
import np.gov.digital.platformidcard.enums.DocumentStatus;
import np.gov.digital.platformidcard.enums.DocumentType;
import np.gov.digital.platformidcard.exception.InvalidDocumentStateException;
import np.gov.digital.platformidcard.repository.OfficialDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfficialDocumentServiceTest {

    @Mock private OfficialDocumentRepository officialDocumentRepository;
    @Mock private CitizenRepository citizenRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private AuditLogService auditLogService;

    private OfficialDocumentService service;

    private UUID citizenId;
    private Citizen citizen;

    @BeforeEach
    void setUp() {
        service = new OfficialDocumentService(
                officialDocumentRepository, citizenRepository, qrCodeService, auditLogService);

        citizenId = UUID.randomUUID();
        Ward ward = new Ward();
        ward.setWardNo(4);
        citizen = new Citizen();
        citizen.setId(citizenId);
        citizen.setWard(ward);

        lenient().when(officialDocumentRepository.save(any(OfficialDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void initiate_createsPrintPendingDocument() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));

        OfficialDocument result = service.initiate(DocumentType.DISABILITY_CARD, citizenId, UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PRINT_PENDING);
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.DISABILITY_CARD);
        verify(auditLogService).log(any(), eq(citizenId), anyString());
    }

    @Test
    void approveAndIssue_cardType_setsExpiryThreeYearsOut() {
        UUID documentId = UUID.randomUUID();
        OfficialDocument doc = OfficialDocument.builder()
                .id(documentId).documentType(DocumentType.DISABILITY_CARD)
                .citizen(citizen).status(DocumentStatus.PRINT_PENDING).build();
        when(officialDocumentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        when(qrCodeService.buildSignedToken(anyString(), anyString(), anyString())).thenReturn("signed-token");

        OfficialDocument result = service.approveAndIssue(documentId, UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(result.getQrToken()).isEqualTo("signed-token");
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(3 * 364, ChronoUnit.DAYS));
    }

    @Test
    void approveAndIssue_alreadyIssued_throwsInvalidState() {
        UUID documentId = UUID.randomUUID();
        OfficialDocument doc = OfficialDocument.builder()
                .id(documentId).documentType(DocumentType.DISABILITY_CARD)
                .citizen(citizen).status(DocumentStatus.ISSUED).build();
        when(officialDocumentRepository.findById(documentId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.approveAndIssue(documentId, UUID.randomUUID()))
                .isInstanceOf(InvalidDocumentStateException.class);
    }

    @Test
    void issueCertificateForVitalEvent_certificateType_noExpiry() {
        UUID vitalEventId = UUID.randomUUID();
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(qrCodeService.buildSignedToken(anyString(), anyString(), anyString())).thenReturn("signed-token");

        OfficialDocument result = service.issueCertificateForVitalEvent(
                DocumentType.BIRTH_CERTIFICATE, citizenId, vitalEventId, UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(result.getVitalEventId()).isEqualTo(vitalEventId);
        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    void issueCertificateForVitalEvent_cardType_rejected() {
        assertThatThrownBy(() -> service.issueCertificateForVitalEvent(
                DocumentType.DISABILITY_CARD, citizenId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revoke_issuedDocument_marksRevoked() {
        UUID documentId = UUID.randomUUID();
        OfficialDocument doc = OfficialDocument.builder()
                .id(documentId).documentType(DocumentType.DISABILITY_CARD)
                .citizen(citizen).status(DocumentStatus.ISSUED).build();
        when(officialDocumentRepository.findById(documentId)).thenReturn(Optional.of(doc));

        OfficialDocument result = service.revoke(documentId, UUID.randomUUID(), "lost card");

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.REVOKED);
        assertThat(result.getRevocationReason()).isEqualTo("lost card");
    }

    @Test
    void revoke_notYetIssued_throwsInvalidState() {
        UUID documentId = UUID.randomUUID();
        OfficialDocument doc = OfficialDocument.builder()
                .id(documentId).documentType(DocumentType.DISABILITY_CARD)
                .citizen(citizen).status(DocumentStatus.PRINT_PENDING).build();
        when(officialDocumentRepository.findById(documentId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.revoke(documentId, UUID.randomUUID(), "reason"))
                .isInstanceOf(InvalidDocumentStateException.class);
    }

    @Test
    void verify_revokedDocument_reportsInvalidEvenWithValidSignature() {
        // This is the exact bug the old ID-card verify endpoint had: a
        // cryptographically valid token for a since-revoked document must
        // now report invalid.
        String issuedDateStr = "2026-01-01";
        when(qrCodeService.verifyToken("tok")).thenReturn(
                new QrCodeService.VerifyResult(true, citizenId.toString(), "DISABILITY_CARD", issuedDateStr, null));

        OfficialDocument revoked = OfficialDocument.builder()
                .id(UUID.randomUUID()).documentType(DocumentType.DISABILITY_CARD)
                .citizen(citizen).status(DocumentStatus.REVOKED).build();
        when(officialDocumentRepository.findByCitizenIdAndDocumentTypeAndIssuedAtBetweenOrderByIssuedAtDesc(
                eq(citizenId), eq(DocumentType.DISABILITY_CARD), any(), any()))
                .thenReturn(List.of(revoked));

        OfficialDocumentService.VerifyResult result = service.verify("tok");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("DOCUMENT_REVOKED");
    }

    @Test
    void verify_invalidSignature_reportsInvalidWithoutDbLookup() {
        when(qrCodeService.verifyToken("bad-tok")).thenReturn(
                new QrCodeService.VerifyResult(false, null, null, null, "SIGNATURE_MISMATCH"));

        OfficialDocumentService.VerifyResult result = service.verify("bad-tok");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("SIGNATURE_MISMATCH");
        verifyNoInteractions(officialDocumentRepository);
    }

    @Test
    void verify_validIssuedDocument_reportsValid() {
        String issuedDateStr = "2026-01-01";
        when(qrCodeService.verifyToken("tok")).thenReturn(
                new QrCodeService.VerifyResult(true, citizenId.toString(), "DISABILITY_CARD", issuedDateStr, null));

        OfficialDocument issued = OfficialDocument.builder()
                .id(UUID.randomUUID()).documentType(DocumentType.DISABILITY_CARD)
                .citizen(citizen).status(DocumentStatus.ISSUED).build();
        when(officialDocumentRepository.findByCitizenIdAndDocumentTypeAndIssuedAtBetweenOrderByIssuedAtDesc(
                eq(citizenId), eq(DocumentType.DISABILITY_CARD), any(), any()))
                .thenReturn(List.of(issued));

        OfficialDocumentService.VerifyResult result = service.verify("tok");

        assertThat(result.valid()).isTrue();
        assertThat(result.document()).isEqualTo(issued);
    }
}
