package np.gov.digital.platformgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.dto.AddPurposeRequest;
import np.gov.digital.platformgateway.dto.CreateRelyingPartyRequest;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.entity.RelyingPartyPurpose;
import np.gov.digital.platformgateway.enums.OrganizationType;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.exception.DualSignoffException;
import np.gov.digital.platformgateway.exception.RelyingPartyNotFoundException;
import np.gov.digital.platformgateway.repository.RelyingPartyPurposeRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelyingPartyServiceTest {

    @Mock private RelyingPartyRepository relyingPartyRepository;
    @Mock private RelyingPartyPurposeRepository relyingPartyPurposeRepository;
    @Mock private AuditLogService auditLogService;

    private RelyingPartyService service;
    private UUID centralAdminId;

    @BeforeEach
    void setUp() {
        service = new RelyingPartyService(
                relyingPartyRepository, relyingPartyPurposeRepository, auditLogService, new ObjectMapper());
        centralAdminId = UUID.randomUUID();

        lenient().when(relyingPartyRepository.save(any(RelyingParty.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(relyingPartyPurposeRepository.save(any(RelyingPartyPurpose.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_licensesRelyingPartyAndReturnsOneTimeSecret() {
        var result = service.create(CreateRelyingPartyRequest.builder()
                .name("Nepal Rastra Bank")
                .organizationType(OrganizationType.GOVERNMENT)
                .build(), centralAdminId);

        assertThat(result.getClientId()).startsWith("rp-");
        assertThat(result.getClientSecret()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo(RelyingPartyStatus.ACTIVE);
        verify(auditLogService).log(any(), isNull(), contains("Nepal Rastra Bank"));
    }

    @Test
    void create_secretIsBcryptHashedNotStoredPlaintext() {
        var result = service.create(CreateRelyingPartyRequest.builder()
                .name("Test Bank").organizationType(OrganizationType.COMMERCIAL).build(), centralAdminId);

        var captor = org.mockito.ArgumentCaptor.forClass(RelyingParty.class);
        verify(relyingPartyRepository).save(captor.capture());

        String storedHash = captor.getValue().getClientSecretHash();
        assertThat(storedHash).isNotEqualTo(result.getClientSecret());
        assertThat(new BCryptPasswordEncoder().matches(result.getClientSecret(), storedHash)).isTrue();
    }

    @Test
    void addPurpose_unknownRelyingParty_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(relyingPartyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPurpose(id, AddPurposeRequest.builder()
                .purposeCode("KYC").allowedFields(List.of("nameEn")).build()))
                .isInstanceOf(RelyingPartyNotFoundException.class);
    }

    @Test
    void addPurpose_serializesAllowedFieldsAsJson() {
        UUID id = UUID.randomUUID();
        RelyingParty relyingParty = RelyingParty.builder().id(id).build();
        when(relyingPartyRepository.findById(id)).thenReturn(Optional.of(relyingParty));

        var purpose = service.addPurpose(id, AddPurposeRequest.builder()
                .purposeCode("KYC").allowedFields(List.of("nameEn", "dob")).build());

        assertThat(purpose.getAllowedFields()).contains("nameEn").contains("dob");
        assertThat(purpose.getRequiresConsent()).isTrue();
    }

    @Test
    void revoke_marksStatusRevokedAndRecordsReason() {
        UUID id = UUID.randomUUID();
        RelyingParty relyingParty = RelyingParty.builder().id(id).status(RelyingPartyStatus.ACTIVE).build();
        when(relyingPartyRepository.findById(id)).thenReturn(Optional.of(relyingParty));

        service.revoke(id, "Repeated fraudulent verify attempts");

        assertThat(relyingParty.getStatus()).isEqualTo(RelyingPartyStatus.REVOKED);
        assertThat(relyingParty.getSuspensionReason()).isEqualTo("Repeated fraudulent verify attempts");
        verify(auditLogService).log(any(), isNull(), contains(id.toString()));
    }

    @Test
    void approveNoConsentException_firstApproval_recordsButDoesNotFlipFlag() {
        UUID purposeId = UUID.randomUUID();
        UUID firstApprover = UUID.randomUUID();
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder().id(purposeId).requiresConsent(true).build();
        when(relyingPartyPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));

        var result = service.approveNoConsentException(purposeId, firstApprover);

        assertThat(result.getNoConsentApprovedBy1()).isEqualTo(firstApprover);
        assertThat(result.getNoConsentApprovedBy2()).isNull();
        assertThat(result.getRequiresConsent()).isTrue();
    }

    @Test
    void approveNoConsentException_secondDistinctApproval_flipsRequiresConsentFalse() {
        UUID purposeId = UUID.randomUUID();
        UUID firstApprover = UUID.randomUUID();
        UUID secondApprover = UUID.randomUUID();
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .id(purposeId).requiresConsent(true).noConsentApprovedBy1(firstApprover).build();
        when(relyingPartyPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));

        var result = service.approveNoConsentException(purposeId, secondApprover);

        assertThat(result.getNoConsentApprovedBy2()).isEqualTo(secondApprover);
        assertThat(result.getRequiresConsent()).isFalse();
    }

    @Test
    void approveNoConsentException_sameAdminTwice_throwsDualSignoffException() {
        UUID purposeId = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .id(purposeId).requiresConsent(true).noConsentApprovedBy1(approver).build();
        when(relyingPartyPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));

        assertThatThrownBy(() -> service.approveNoConsentException(purposeId, approver))
                .isInstanceOf(DualSignoffException.class);
    }

    @Test
    void approveNoConsentException_alreadyFullyApproved_throwsIllegalState() {
        UUID purposeId = UUID.randomUUID();
        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .id(purposeId).requiresConsent(false)
                .noConsentApprovedBy1(UUID.randomUUID())
                .noConsentApprovedBy2(UUID.randomUUID())
                .build();
        when(relyingPartyPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));

        assertThatThrownBy(() -> service.approveNoConsentException(purposeId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listRevoked_returnsOnlyRevokedStatusParties() {
        RelyingParty revoked = RelyingParty.builder().status(RelyingPartyStatus.REVOKED).build();
        when(relyingPartyRepository.findByStatus(RelyingPartyStatus.REVOKED)).thenReturn(List.of(revoked));

        assertThat(service.listRevoked()).containsExactly(revoked);
    }

    @Test
    void getById_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(relyingPartyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(RelyingPartyNotFoundException.class);
    }
}
