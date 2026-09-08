package np.gov.digital.platformgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.dto.AddPurposeRequest;
import np.gov.digital.platformgateway.dto.CreateRelyingPartyRequest;
import np.gov.digital.platformgateway.dto.CreateRelyingPartyResponse;
import np.gov.digital.platformgateway.dto.RelyingPartyResponse;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.entity.RelyingPartyPurpose;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.exception.RelyingPartyNotFoundException;
import np.gov.digital.platformgateway.repository.RelyingPartyPurposeRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import np.gov.digital.platformgateway.exception.DualSignoffException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Central-Admin-only licensing (Extended Modules §6.1-6.2). See V41's
 * migration comment for the mTLS scope boundary — certificate
 * fingerprint/expiry are stored and checked at the application level,
 * not enforced at the TLS handshake.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelyingPartyService {

    private final RelyingPartyRepository relyingPartyRepository;
    private final RelyingPartyPurposeRepository relyingPartyPurposeRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public CreateRelyingPartyResponse create(CreateRelyingPartyRequest request, UUID centralAdminId) {
        String clientId = "rp-" + UUID.randomUUID();
        String clientSecret = UUID.randomUUID() + "-" + UUID.randomUUID();

        RelyingParty relyingParty = RelyingParty.builder()
                .name(request.getName())
                .organizationType(request.getOrganizationType())
                .clientId(clientId)
                .clientSecretHash(passwordEncoder.encode(clientSecret))
                .status(RelyingPartyStatus.ACTIVE)
                .licensedBy(centralAdminId)
                .build();

        RelyingParty saved = relyingPartyRepository.save(relyingParty);

        // No citizenId — this event is about an external organization,
        // not any specific citizen (citizen_events.citizen_id is
        // nullable for exactly this case, per V15's own schema comment).
        auditLogService.log(AuditEventType.RELYING_PARTY_LICENSED, null,
                request.getOrganizationType() + " relying party licensed: " + request.getName());
        log.info("Relying party licensed — id: {}, name: {}, type: {}",
                saved.getId(), request.getName(), request.getOrganizationType());

        // NOTE: the plaintext clientSecret is returned exactly once, here
        // — never stored, never retrievable again, same discipline as a
        // user's own password.
        return CreateRelyingPartyResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .organizationType(saved.getOrganizationType())
                .clientId(clientId)
                .clientSecret(clientSecret)
                .status(saved.getStatus())
                .build();
    }

    @Transactional
    public RelyingPartyPurpose addPurpose(UUID relyingPartyId, AddPurposeRequest request) {
        RelyingParty relyingParty = relyingPartyRepository.findById(relyingPartyId)
                .orElseThrow(() -> new RelyingPartyNotFoundException(relyingPartyId));

        RelyingPartyPurpose purpose = RelyingPartyPurpose.builder()
                .relyingParty(relyingParty)
                .purposeCode(request.getPurposeCode())
                .allowedFields(toJson(request.getAllowedFields()))
                .build();

        RelyingPartyPurpose saved = relyingPartyPurposeRepository.save(purpose);
        log.info("Purpose added — relyingParty: {}, purposeCode: {}, fields: {}",
                relyingPartyId, request.getPurposeCode(), request.getAllowedFields());
        return saved;
    }

    @Transactional(readOnly = true)
    public RelyingPartyResponse getById(UUID id) {
        return toResponse(relyingPartyRepository.findById(id)
                .orElseThrow(() -> new RelyingPartyNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public List<RelyingParty> listRevoked() {
        return relyingPartyRepository.findByStatus(RelyingPartyStatus.REVOKED);
    }

    /** Graduated penalty ladder's final rung (§6.5) — terminal, never reversible. */
    @Transactional
    public void revoke(UUID relyingPartyId, String reason) {
        RelyingParty relyingParty = relyingPartyRepository.findById(relyingPartyId)
                .orElseThrow(() -> new RelyingPartyNotFoundException(relyingPartyId));
        relyingParty.setStatus(RelyingPartyStatus.REVOKED);
        relyingParty.setSuspensionReason(reason);
        relyingPartyRepository.save(relyingParty);

        auditLogService.log(AuditEventType.RELYING_PARTY_REVOKED, null,
                "Relying party " + relyingPartyId + " revoked: " + reason);
        log.warn("Relying party REVOKED — id: {}, reason: {}", relyingPartyId, reason);
    }

    /**
     * The narrow legal-exception path (§6.4): setting requiresConsent to
     * false needs two DISTINCT Central Admins' sign-off. The first call
     * records the first approver and leaves requiresConsent unchanged;
     * only the second call, from someone else, actually flips it.
     */
    @Transactional
    public RelyingPartyPurpose approveNoConsentException(UUID purposeId, UUID approverId) {
        RelyingPartyPurpose purpose = relyingPartyPurposeRepository.findById(purposeId)
                .orElseThrow(() -> new IllegalArgumentException("No purpose with ID: " + purposeId));

        if (purpose.getNoConsentApprovedBy1() == null) {
            purpose.setNoConsentApprovedBy1(approverId);
        } else if (purpose.getNoConsentApprovedBy1().equals(approverId)) {
            throw new DualSignoffException(
                    "This purpose's no-consent exception was already first-approved by this same admin — "
                            + "a second, different Central Admin must provide the second sign-off.");
        } else if (purpose.getNoConsentApprovedBy2() == null) {
            purpose.setNoConsentApprovedBy2(approverId);
            purpose.setRequiresConsent(false);
        } else {
            throw new IllegalStateException("This purpose's no-consent exception is already fully approved.");
        }

        return relyingPartyPurposeRepository.save(purpose);
    }

    private String toJson(List<String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize allowed fields", e);
        }
    }

    private RelyingPartyResponse toResponse(RelyingParty rp) {
        return RelyingPartyResponse.builder()
                .id(rp.getId())
                .name(rp.getName())
                .organizationType(rp.getOrganizationType())
                .clientId(rp.getClientId())
                .status(rp.getStatus())
                .suspensionReason(rp.getSuspensionReason())
                .certificateExpiresAt(rp.getCertificateExpiresAt())
                .consecutiveDeniedCount(rp.getConsecutiveDeniedCount())
                .licensedAt(rp.getLicensedAt())
                .build();
    }
}
