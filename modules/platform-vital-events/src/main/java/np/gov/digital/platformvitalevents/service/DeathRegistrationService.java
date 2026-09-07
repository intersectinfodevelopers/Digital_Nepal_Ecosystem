package np.gov.digital.platformvitalevents.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.repository.WardRepository;
import np.gov.digital.citizen.service.CitizenService;
import np.gov.digital.citizen.service.EligibilityService;
import np.gov.digital.household.entity.Household;
import np.gov.digital.household.repository.HouseholdRepository;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformidcard.enums.DocumentType;
import np.gov.digital.platformidcard.service.OfficialDocumentService;
import np.gov.digital.platformvitalevents.dto.DeathApprovalResponse;
import np.gov.digital.platformvitalevents.dto.DeathRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.DeathRegistrationResponse;
import np.gov.digital.platformvitalevents.dto.VerbalAutopsyRequest;
import np.gov.digital.platformvitalevents.entity.DeathRecord;
import np.gov.digital.platformvitalevents.entity.VerbalAutopsyResponse;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;
import np.gov.digital.platformvitalevents.exception.DuplicateDeathRecordException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.repository.DeathRecordRepository;
import np.gov.digital.platformvitalevents.repository.VerbalAutopsyResponseRepository;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Death registration (SDD Extended Modules §4.3).
 *
 * approve()'s cascade — what's real in this increment, and what isn't:
 *   - citizen archived (status -> DECEASED): DONE — CitizenService.markDeceased().
 *   - spouse eligibility re-run: DONE, if the deceased has spouseCitizenId
 *     set — calls the existing EligibilityService.evaluate() for the
 *     spouse (it already recomputes from scratch each call; there is no
 *     separate "stored decision" to invalidate).
 *   - head-of-household reassignment flagged: DONE, as a flag — every
 *     household this citizen was head of is logged as needing
 *     reassignment (via citizen_events) and returned in the approval
 *     response; nothing auto-assigns a new head, matching the SDD's own
 *     wording ("flagged", not "reassigned").
 *   - ID cards revoked: NOT implemented. There is no id_card table or
 *     entity anywhere in this codebase yet — V3__identity_tables.sql left
 *     it as an unwritten placeholder comment, and platform-idcard has no
 *     repository/entity backing it, only a controller/PDF generator/QR
 *     service. Nothing to revoke against.
 *   - benefits closed: NOT implemented. platform-eligibility is an empty
 *     module (a single marker class, no entities) — no benefit or
 *     disbursement record exists to close.
 * The last two are real, not silently dropped — they need their own
 * infrastructure (Phase 3's benefit_disbursement, and an actual id_card
 * table) before this cascade can cover them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeathRegistrationService {

    private final VitalEventRepository vitalEventRepository;
    private final DeathRecordRepository deathRecordRepository;
    private final VerbalAutopsyResponseRepository verbalAutopsyResponseRepository;
    private final WardRepository wardRepository;
    private final CitizenRepository citizenRepository;
    private final HouseholdRepository householdRepository;
    private final VitalEventService vitalEventService;
    private final CitizenService citizenService;
    private final EligibilityService eligibilityService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final OfficialDocumentService officialDocumentService;

    @Transactional
    public DeathRegistrationResponse register(DeathRegistrationRequest request) {

        Ward ward = wardRepository.findById(request.getWardId())
                .orElseThrow(() -> new WardNotFoundException(request.getWardId()));

        Citizen deceased = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getCitizenId()));

        List<DeathRecord> existing = deathRecordRepository.findActiveByCitizenId(deceased.getId());
        if (!existing.isEmpty()) {
            throw new DuplicateDeathRecordException(
                    "A death has already been registered for this citizen (ERR_DEATH_ALREADY_RECORDED).");
        }

        UUID actorId = getActorId();

        VitalEvent vitalEvent = VitalEvent.builder()
                .eventType(VitalEventType.DEATH)
                .status(VitalEventStatus.SUBMITTED)
                .ward(ward)
                .submittedBy(actorId)
                .build();
        VitalEvent savedEvent = vitalEventRepository.saveAndFlush(vitalEvent);

        DeathRecord deathRecord = DeathRecord.builder()
                .vitalEvent(savedEvent)
                .citizen(deceased)
                .dateOfDeath(LocalDate.parse(request.getDateOfDeath()))
                .placeOfDeath(request.getPlaceOfDeath())
                .immediateCauseOfDeath(request.getImmediateCauseOfDeath())
                .mannerOfDeath(request.getMannerOfDeath())
                .informantName(request.getInformantName())
                .informantRelation(request.getInformantRelation())
                .certifyingFacility(request.getCertifyingFacility())
                .build();
        deathRecordRepository.save(deathRecord);

        VitalEvent pendingEvent = vitalEventService.submitForApproval(savedEvent);

        log.info("Death registration submitted — vitalEventId: {}, citizenId: {}",
                pendingEvent.getId(), deceased.getId());

        return DeathRegistrationResponse.builder()
                .vitalEventId(pendingEvent.getId())
                .status(pendingEvent.getStatus())
                .citizenId(deceased.getId())
                .wardId(ward.getId())
                .submittedAt(pendingEvent.getSubmittedAt())
                .message("Death registration submitted for approval.")
                .build();
    }

    @Transactional
    public DeathApprovalResponse approve(UUID vitalEventId, UUID approverId) {
        DeathRecord deathRecord = deathRecordRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));

        // Final race-window backstop — see this class's Javadoc.
        List<DeathRecord> stillActive = deathRecordRepository.findActiveByCitizenId(
                deathRecord.getCitizen().getId());
        boolean anotherAlreadyApproved = stillActive.stream()
                .anyMatch(dr -> !dr.getVitalEventId().equals(vitalEventId)
                        && dr.getVitalEvent().getStatus() == VitalEventStatus.APPROVED);
        if (anotherAlreadyApproved) {
            throw new DuplicateDeathRecordException(
                    "Another death registration for this citizen was already approved (ERR_DEATH_ALREADY_RECORDED).");
        }

        vitalEventService.approve(vitalEventId, approverId);

        UUID citizenId = deathRecord.getCitizen().getId();
        citizenService.markDeceased(citizenId, approverId);

        // Spouse eligibility re-run — see class Javadoc.
        UUID spouseCitizenId = deathRecord.getCitizen().getSpouseCitizenId();
        boolean spouseReevaluated = false;
        if (spouseCitizenId != null) {
            try {
                eligibilityService.evaluate(spouseCitizenId);
                spouseReevaluated = true;
            } catch (Exception e) {
                log.warn("Could not re-evaluate spouse eligibility for citizen {} after death of {}: {}",
                        spouseCitizenId, citizenId, e.getMessage());
            }
        }

        // Head-of-household reassignment flag — see class Javadoc.
        List<Household> householdsToFlag = householdRepository.findByHeadCitizenId(citizenId);
        for (Household household : householdsToFlag) {
            auditLogService.log(
                    AuditEventType.CITIZEN_UPDATED,
                    citizenId,
                    "Household " + household.getId() + " needs a new head-of-household — "
                            + "previous head deceased (vitalEventId: " + vitalEventId + ")"
            );
        }

        // Extended Modules §4.7 — the vital event's own approval IS the
        // authorization; a certificate doesn't need a second review.
        officialDocumentService.issueCertificateForVitalEvent(
                DocumentType.DEATH_CERTIFICATE, citizenId, vitalEventId, approverId);

        log.info("Death event approved — vitalEventId: {}, citizenId: {}, spouseReevaluated: {}, householdsFlagged: {}",
                vitalEventId, citizenId, spouseReevaluated, householdsToFlag.size());

        return DeathApprovalResponse.builder()
                .citizenId(citizenId)
                .spouseEligibilityReevaluated(spouseReevaluated)
                .spouseCitizenId(spouseCitizenId)
                .householdsFlaggedForReassignment(householdsToFlag.size())
                .build();
    }

    @Transactional
    public void reject(UUID vitalEventId, UUID approverId, String reason) {
        vitalEventService.reject(vitalEventId, approverId, reason);
    }

    @Transactional
    public void attachVerbalAutopsy(UUID vitalEventId, VerbalAutopsyRequest request) {
        DeathRecord deathRecord = deathRecordRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));

        VerbalAutopsyResponse va = VerbalAutopsyResponse.builder()
                .deathRecord(deathRecord)
                .respondentName(request.getRespondentName())
                .respondentRelation(request.getRespondentRelation())
                .interviewDate(LocalDate.parse(request.getInterviewDate()))
                .responses(toJson(request.getResponses()))
                .probableCauseOfDeath(request.getProbableCauseOfDeath())
                .build();

        verbalAutopsyResponseRepository.save(va);

        log.info("Verbal autopsy attached — vitalEventId: {}", vitalEventId);
    }

    private String toJson(java.util.Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize verbal autopsy responses", e);
        }
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
