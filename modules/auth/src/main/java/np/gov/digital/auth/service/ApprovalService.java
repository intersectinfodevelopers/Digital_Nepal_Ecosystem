package np.gov.digital.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.auth.dto.CitizenEditRequestDto;
import np.gov.digital.auth.entity.CitizenEditRequest;
import np.gov.digital.auth.enums.ApprovalStatus;
import np.gov.digital.auth.exception.SelfApprovalException;
import np.gov.digital.auth.repository.CitizenEditRequestRepository;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.enums.DigitalLiteracy;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Citizen edit-request workflow: submit → pending → approve/reject.
 *
 * approve() used to only flip the request's own status — changePayload was
 * stored and never read, so "approving" an edit never actually changed the
 * citizen record it was about. This version applies the change for real.
 *
 * Only a fixed whitelist of low-risk fields can be edited this way. NID,
 * citizenship number, DOB, sex, and ward are deliberately excluded — those
 * are identity-critical and encryption/dedup-linked; changing them needs a
 * dedicated, more heavily audited re-verification flow, not a generic
 * field-diff approval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final CitizenEditRequestRepository repository;
    private final CitizenRepository citizenRepository;
    private final NidEncryptionUtil nidEncryptionUtil;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private static final Set<String> ENCRYPTED_FIELDS = Set.of("phone", "phoneAlt", "email");

    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "nameNp", "nameEn", "bloodGroup", "religion", "ethnicity",
            "motherTongue", "tole", "phone", "phoneAlt", "email",
            "digitalLiteracy", "hasSmartphone", "photoUrl"
    );

    public CitizenEditRequest submit(
            UUID submittedBy,
            CitizenEditRequestDto request) {

        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .filter(Citizen::getIsActive)
                .orElseThrow(() -> new CitizenNotFoundException(request.getCitizenId()));

        Map<String, Object> requestedChanges = rejectNonEditableFields(request.getChanges());
        Map<String, Object> oldValues = snapshotCurrentValues(citizen, requestedChanges.keySet());

        CitizenEditRequest editRequest = CitizenEditRequest.builder()
                .citizenId(citizen.getId())
                .wardId(citizen.getWard().getId())
                .submittedBy(submittedBy)
                .changePayload(toJson(requestedChanges))
                .oldValueJson(toJson(oldValues))
                .status(ApprovalStatus.PENDING_APPROVAL)
                .build();

        CitizenEditRequest saved = repository.save(editRequest);

        auditLogService.log(
                AuditEventType.EDIT_SUBMITTED,
                citizen.getId(),
                "Edit request submitted for fields: " + requestedChanges.keySet()
        );

        return saved;
    }

    public CitizenEditRequest approve(UUID requestId, UUID approverId) {

        CitizenEditRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Backed by the no_self_approval CHECK constraint on
        // citizen_edit_requests (V17) — checked here first for a clean
        // error rather than a raw DataIntegrityViolationException; the DB
        // constraint stays as the real guarantee regardless of this check.
        if (approverId.equals(request.getSubmittedBy())) {
            throw new SelfApprovalException(
                    "Cannot approve an edit request you submitted yourself.");
        }

        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getCitizenId()));

        Map<String, Object> changes = fromJson(request.getChangePayload());
        applyChanges(citizen, changes);
        citizen.setVersionNumber(
                citizen.getVersionNumber() != null ? citizen.getVersionNumber() + 1 : 2);
        citizenRepository.save(citizen);

        request.setApprovedBy(approverId);
        request.setStatus(ApprovalStatus.APPROVED);
        CitizenEditRequest saved = repository.save(request);

        auditLogService.log(
                AuditEventType.EDIT_APPROVED,
                citizen.getId(),
                "Edit request approved — fields updated: " + changes.keySet()
        );

        log.info("Edit request {} approved and applied to citizen {}", requestId, citizen.getId());

        return saved;
    }

    public CitizenEditRequest reject(
            UUID requestId,
            UUID approverId,
            String reason) {

        CitizenEditRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        request.setApprovedBy(approverId);
        request.setStatus(ApprovalStatus.REJECTED);
        request.setRejectionReason(reason);

        CitizenEditRequest saved = repository.save(request);

        auditLogService.log(
                AuditEventType.EDIT_REJECTED,
                request.getCitizenId(),
                "Edit request rejected: " + reason
        );

        return saved;
    }

    public List<CitizenEditRequest> pendingRequests() {
        return repository.findByStatus(
                ApprovalStatus.PENDING_APPROVAL);
    }

    // PRIVATE HELPERS

    private Map<String, Object> rejectNonEditableFields(Map<String, Object> changes) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            if (EDITABLE_FIELDS.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            } else {
                log.warn("Ignoring edit request for non-editable field: {}", entry.getKey());
            }
        }
        return filtered;
    }

    private Map<String, Object> snapshotCurrentValues(Citizen citizen, Set<String> fields) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String field : fields) {
            Object currentValue = switch (field) {
                case "nameNp" -> citizen.getNameNp();
                case "nameEn" -> citizen.getNameEn();
                case "bloodGroup" -> citizen.getBloodGroup();
                case "religion" -> citizen.getReligion();
                case "ethnicity" -> citizen.getEthnicity();
                case "motherTongue" -> citizen.getMotherTongue();
                case "tole" -> citizen.getTole();
                case "phone" -> citizen.getPhoneEnc() != null ? nidEncryptionUtil.decrypt(citizen.getPhoneEnc()) : null;
                case "phoneAlt" -> citizen.getPhoneAltEnc() != null ? nidEncryptionUtil.decrypt(citizen.getPhoneAltEnc()) : null;
                case "email" -> citizen.getEmailEnc() != null ? nidEncryptionUtil.decrypt(citizen.getEmailEnc()) : null;
                case "digitalLiteracy" -> citizen.getDigitalLiteracy();
                case "hasSmartphone" -> citizen.getHasSmartphone();
                case "photoUrl" -> citizen.getPhotoUrl();
                default -> null;
            };
            snapshot.put(field, currentValue);
        }
        return snapshot;
    }

    private void applyChanges(Citizen citizen, Map<String, Object> changes) {
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String field = entry.getKey();
            if (!EDITABLE_FIELDS.contains(field)) {
                // Defense in depth — submit() already filtered this, but an
                // edit request approved long after submission shouldn't
                // trust a whitelist that might have narrowed since.
                log.warn("Skipping non-editable field at approval time: {}", field);
                continue;
            }

            Object value = entry.getValue();
            String stringValue = value != null ? value.toString() : null;

            switch (field) {
                case "nameNp" -> citizen.setNameNp(stringValue);
                case "nameEn" -> citizen.setNameEn(stringValue);
                case "bloodGroup" -> citizen.setBloodGroup(stringValue);
                case "religion" -> citizen.setReligion(stringValue);
                case "ethnicity" -> citizen.setEthnicity(stringValue);
                case "motherTongue" -> citizen.setMotherTongue(stringValue);
                case "tole" -> citizen.setTole(stringValue);
                case "phone" -> citizen.setPhoneEnc(stringValue != null ? nidEncryptionUtil.encrypt(stringValue) : null);
                case "phoneAlt" -> citizen.setPhoneAltEnc(stringValue != null ? nidEncryptionUtil.encrypt(stringValue) : null);
                case "email" -> citizen.setEmailEnc(stringValue != null ? nidEncryptionUtil.encrypt(stringValue) : null);
                case "digitalLiteracy" -> citizen.setDigitalLiteracy(stringValue != null ? DigitalLiteracy.valueOf(stringValue) : null);
                case "hasSmartphone" -> citizen.setHasSmartphone(value != null ? Boolean.valueOf(stringValue) : null);
                case "photoUrl" -> citizen.setPhotoUrl(stringValue);
                default -> log.warn("No apply-handler wired for editable field: {}", field);
            }
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize edit request payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse stored edit request payload", e);
        }
    }
}
