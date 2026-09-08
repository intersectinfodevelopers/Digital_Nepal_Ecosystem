package np.gov.digital.platformevidenceexchange.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformevidenceexchange.dto.CreateEvidenceRequestRequest;
import np.gov.digital.platformevidenceexchange.dto.EvidenceRequestCallbackRequest;
import np.gov.digital.platformevidenceexchange.entity.EvidenceRequest;
import np.gov.digital.platformevidenceexchange.enums.EvidenceAgency;
import np.gov.digital.platformevidenceexchange.enums.EvidencePurpose;
import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;
import np.gov.digital.platformevidenceexchange.exception.EvidenceRequestNotFoundException;
import np.gov.digital.platformevidenceexchange.exception.InvalidEvidenceRequestTransitionException;
import np.gov.digital.platformevidenceexchange.exception.MismatchedPurposeAgencyException;
import np.gov.digital.platformevidenceexchange.repository.EvidenceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceRequestServiceTest {

    @Mock private EvidenceRequestRepository evidenceRequestRepository;
    @Mock private CitizenRepository citizenRepository;
    @Mock private AuditLogService auditLogService;

    private EvidenceRequestService service;

    private UUID citizenId;
    private Citizen citizen;

    @BeforeEach
    void setUp() {
        service = new EvidenceRequestService(
                evidenceRequestRepository, citizenRepository, auditLogService, new ObjectMapper());

        citizenId = UUID.randomUUID();
        Ward ward = new Ward();
        ward.setWardNo(2);
        citizen = new Citizen();
        citizen.setId(citizenId);
        citizen.setWard(ward);

        lenient().when(evidenceRequestRepository.save(any(EvidenceRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_matchingPurposeAndAgency_createsPendingRequest() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));

        var result = service.create(CreateEvidenceRequestRequest.builder()
                .citizenId(citizenId)
                .targetAgency(EvidenceAgency.DAO)
                .purpose(EvidencePurpose.CITIZENSHIP_VERIFICATION)
                .factRequested("Is citizenship number 12-34-56-78901 valid?")
                .build());

        assertThat(result.getStatus()).isEqualTo(EvidenceRequestStatus.PENDING);
        // requestedAt itself is populated by @PrePersist, which a mocked
        // repository.save() never triggers — expiresAt is what the
        // service sets explicitly, so that's what's checked here.
        assertThat(result.getExpiresAt()).isAfter(java.time.Instant.now());
        verify(auditLogService).log(any(), eq(citizenId), anyString());
    }

    @Test
    void create_mismatchedPurposeAndAgency_throws() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));

        assertThatThrownBy(() -> service.create(CreateEvidenceRequestRequest.builder()
                .citizenId(citizenId)
                .targetAgency(EvidenceAgency.ELECTION_COMMISSION)
                .purpose(EvidencePurpose.CITIZENSHIP_VERIFICATION)
                .factRequested("test")
                .build()))
                .isInstanceOf(MismatchedPurposeAgencyException.class);

        verify(evidenceRequestRepository, never()).save(any());
    }

    @Test
    void applyCallback_success_marksRespondedWithPayload() {
        UUID requestId = UUID.randomUUID();
        EvidenceRequest evidenceRequest = EvidenceRequest.builder()
                .id(requestId).citizen(citizen).targetAgency(EvidenceAgency.DAO)
                .purpose(EvidencePurpose.CITIZENSHIP_VERIFICATION)
                .status(EvidenceRequestStatus.PENDING).build();
        when(evidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(evidenceRequest));

        var result = service.applyCallback(requestId, EvidenceRequestCallbackRequest.builder()
                .success(true)
                .responsePayload(Map.of("citizenship_valid", true))
                .build());

        assertThat(result.getStatus()).isEqualTo(EvidenceRequestStatus.RESPONDED);
        assertThat(result.getResponsePayload()).contains("citizenship_valid");
    }

    @Test
    void applyCallback_failure_marksFailedWithReason() {
        UUID requestId = UUID.randomUUID();
        EvidenceRequest evidenceRequest = EvidenceRequest.builder()
                .id(requestId).citizen(citizen).targetAgency(EvidenceAgency.DAO)
                .purpose(EvidencePurpose.CITIZENSHIP_VERIFICATION)
                .status(EvidenceRequestStatus.PENDING).build();
        when(evidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(evidenceRequest));

        var result = service.applyCallback(requestId, EvidenceRequestCallbackRequest.builder()
                .success(false).failureReason("Record not found at DAO").build());

        assertThat(result.getStatus()).isEqualTo(EvidenceRequestStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("Record not found at DAO");
    }

    @Test
    void applyCallback_onAlreadyResponded_throwsInvalidTransition() {
        UUID requestId = UUID.randomUUID();
        EvidenceRequest evidenceRequest = EvidenceRequest.builder()
                .id(requestId).citizen(citizen).targetAgency(EvidenceAgency.DAO)
                .purpose(EvidencePurpose.CITIZENSHIP_VERIFICATION)
                .status(EvidenceRequestStatus.RESPONDED).build();
        when(evidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(evidenceRequest));

        assertThatThrownBy(() -> service.applyCallback(requestId,
                EvidenceRequestCallbackRequest.builder().success(true).build()))
                .isInstanceOf(InvalidEvidenceRequestTransitionException.class);
    }

    @Test
    void getById_unknownId_throwsNotFound() {
        UUID missingId = UUID.randomUUID();
        when(evidenceRequestRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(EvidenceRequestNotFoundException.class);
    }
}
