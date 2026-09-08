package np.gov.digital.platformevidenceexchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformevidenceexchange.dto.CreateEvidenceRequestRequest;
import np.gov.digital.platformevidenceexchange.dto.EvidenceRequestCallbackRequest;
import np.gov.digital.platformevidenceexchange.dto.EvidenceRequestResponse;
import np.gov.digital.platformevidenceexchange.entity.EvidenceRequest;
import np.gov.digital.platformevidenceexchange.enums.EvidenceAgency;
import np.gov.digital.platformevidenceexchange.enums.EvidencePurpose;
import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;
import np.gov.digital.platformevidenceexchange.exception.EvidenceRequestNotFoundException;
import np.gov.digital.platformevidenceexchange.exception.MismatchedPurposeAgencyException;
import np.gov.digital.platformevidenceexchange.repository.EvidenceRequestRepository;
import np.gov.digital.platformevidenceexchange.statemachine.EvidenceRequestStateMachine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Once-Only Evidence Exchange (SDD Governance Tiers §7) — one logged,
 * purpose-specific request per fact needed from an external authoritative
 * source. Never a bulk export: this API has no endpoint that creates more
 * than one request, or returns more than one citizen's evidence, in a
 * single call — the audit list (findAllByOrderByRequestedAtDesc) is
 * paginated read access to requests already made individually, not a
 * mechanism for making or fetching many at once.
 *
 * KNOWN LIMITATION, same category as BenefitDisbursementService's: there
 * is no real integration with DAO, NIDMC, or the Election Commission —
 * none of the three publish an API this system has credentials for, and
 * which protocol/hosting a real integration would use is itself
 * downstream of the "Data residency" decision the plan's own Concept
 * Document §15 lists as gating this phase. This service creates the
 * request and exposes a callback endpoint a real integration's response
 * handler would call — the actual outbound call to the external agency,
 * and the mTLS the design doc calls for on both ends of that
 * conversation, don't exist to build against yet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvidenceRequestService {

    private static final int REQUEST_VALIDITY_DAYS = 30;

    private final EvidenceRequestRepository evidenceRequestRepository;
    private final CitizenRepository citizenRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EvidenceRequestResponse create(CreateEvidenceRequestRequest request) {
        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getCitizenId()));

        checkPurposeMatchesAgency(request.getPurpose(), request.getTargetAgency());

        UUID actorId = getActorId();
        Instant now = Instant.now();

        EvidenceRequest evidenceRequest = EvidenceRequest.builder()
                .citizen(citizen)
                .targetAgency(request.getTargetAgency())
                .purpose(request.getPurpose())
                .factRequested(request.getFactRequested())
                .status(EvidenceRequestStatus.PENDING)
                .requestedBy(actorId)
                .expiresAt(now.plus(REQUEST_VALIDITY_DAYS, ChronoUnit.DAYS))
                .build();

        EvidenceRequest saved = evidenceRequestRepository.save(evidenceRequest);

        auditLogService.log(AuditEventType.EVIDENCE_REQUEST_CREATED, request.getCitizenId(),
                request.getPurpose() + " evidence request created to " + request.getTargetAgency()
                        + ": " + request.getFactRequested());

        log.info("Evidence request created — id: {}, citizen: {}, agency: {}, purpose: {}",
                saved.getId(), request.getCitizenId(), request.getTargetAgency(), request.getPurpose());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public EvidenceRequestResponse getById(UUID id) {
        return toResponse(evidenceRequestRepository.findById(id)
                .orElseThrow(() -> new EvidenceRequestNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public Page<EvidenceRequestResponse> auditList(Pageable pageable) {
        return evidenceRequestRepository.findAllByOrderByRequestedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional
    public EvidenceRequestResponse applyCallback(UUID id, EvidenceRequestCallbackRequest callback) {
        EvidenceRequest evidenceRequest = evidenceRequestRepository.findById(id)
                .orElseThrow(() -> new EvidenceRequestNotFoundException(id));

        EvidenceRequestStatus targetStatus = Boolean.TRUE.equals(callback.getSuccess())
                ? EvidenceRequestStatus.RESPONDED : EvidenceRequestStatus.FAILED;
        EvidenceRequestStateMachine.validate(evidenceRequest.getStatus(), targetStatus);

        evidenceRequest.setStatus(targetStatus);
        evidenceRequest.setRespondedAt(Instant.now());
        if (targetStatus == EvidenceRequestStatus.RESPONDED) {
            evidenceRequest.setResponsePayload(toJson(callback.getResponsePayload()));
        } else {
            evidenceRequest.setFailureReason(callback.getFailureReason() != null
                    ? callback.getFailureReason() : "Target agency reported failure with no reason given");
        }

        EvidenceRequest saved = evidenceRequestRepository.save(evidenceRequest);

        auditLogService.log(
                targetStatus == EvidenceRequestStatus.RESPONDED
                        ? AuditEventType.EVIDENCE_REQUEST_RESPONDED
                        : AuditEventType.EVIDENCE_REQUEST_FAILED,
                evidenceRequest.getCitizen().getId(),
                "Evidence request " + id + " " + targetStatus);

        log.info("Evidence request callback applied — id: {}, status: {}", id, targetStatus);
        return toResponse(saved);
    }

    private void checkPurposeMatchesAgency(EvidencePurpose purpose, EvidenceAgency agency) {
        EvidenceAgency expected = switch (purpose) {
            case CITIZENSHIP_VERIFICATION -> EvidenceAgency.DAO;
            case NID_VERIFICATION -> EvidenceAgency.NIDMC;
            case VOTER_ROLL_CHECK -> EvidenceAgency.ELECTION_COMMISSION;
        };
        if (expected != agency) {
            throw new MismatchedPurposeAgencyException(
                    purpose + " must target " + expected + ", not " + agency + ".");
        }
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize evidence response payload", e);
        }
    }

    private EvidenceRequestResponse toResponse(EvidenceRequest e) {
        return EvidenceRequestResponse.builder()
                .id(e.getId())
                .citizenId(e.getCitizen().getId())
                .targetAgency(e.getTargetAgency())
                .purpose(e.getPurpose())
                .factRequested(e.getFactRequested())
                .status(e.getStatus())
                .responsePayload(e.getResponsePayload())
                .failureReason(e.getFailureReason())
                .requestedAt(e.getRequestedAt())
                .respondedAt(e.getRespondedAt())
                .expiresAt(e.getExpiresAt())
                .build();
    }

    private UUID getActorId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedActor actor) {
                return actor.getUserId();
            }
        } catch (Exception e) {
            log.warn("Could not extract actor ID from SecurityContext — using placeholder", e);
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
