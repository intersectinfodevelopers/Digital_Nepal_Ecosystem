package np.gov.digital.platformvitalevents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.enums.MaritalStatus;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.repository.WardRepository;
import np.gov.digital.citizen.service.CitizenService;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformidcard.enums.DocumentType;
import np.gov.digital.platformidcard.service.OfficialDocumentService;
import np.gov.digital.platformvitalevents.dto.DivorceRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.DivorceRegistrationResponse;
import np.gov.digital.platformvitalevents.entity.DivorceRecord;
import np.gov.digital.platformvitalevents.entity.MaritalStatusHistory;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;
import np.gov.digital.platformvitalevents.exception.NotMarriedToEachOtherException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.repository.DivorceRecordRepository;
import np.gov.digital.platformvitalevents.repository.MaritalStatusHistoryRepository;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Divorce registration (SDD Extended Modules §4.5). Requires a court
 * order; deliberately never touches residency — unlike marriage, there is
 * no ward-transfer step here at all.
 *
 * "Both spouses currently married to each other" is checked at both
 * submission and approval, same race-window reasoning as marriage/death.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DivorceRegistrationService {

    private final VitalEventRepository vitalEventRepository;
    private final DivorceRecordRepository divorceRecordRepository;
    private final MaritalStatusHistoryRepository maritalStatusHistoryRepository;
    private final WardRepository wardRepository;
    private final CitizenRepository citizenRepository;
    private final VitalEventService vitalEventService;
    private final CitizenService citizenService;
    private final OfficialDocumentService officialDocumentService;

    @Transactional
    public DivorceRegistrationResponse register(DivorceRegistrationRequest request) {

        Ward ward = wardRepository.findById(request.getWardId())
                .orElseThrow(() -> new WardNotFoundException(request.getWardId()));

        if (request.getSpouse1CitizenId().equals(request.getSpouse2CitizenId())) {
            throw new IllegalArgumentException("A citizen cannot divorce themselves.");
        }

        Citizen spouse1 = citizenRepository.findById(request.getSpouse1CitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getSpouse1CitizenId()));
        Citizen spouse2 = citizenRepository.findById(request.getSpouse2CitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getSpouse2CitizenId()));

        checkMarriedToEachOther(spouse1, spouse2);

        UUID actorId = getActorId();

        VitalEvent vitalEvent = VitalEvent.builder()
                .eventType(VitalEventType.DIVORCE)
                .status(VitalEventStatus.SUBMITTED)
                .ward(ward)
                .submittedBy(actorId)
                .build();
        VitalEvent savedEvent = vitalEventRepository.saveAndFlush(vitalEvent);

        DivorceRecord divorceRecord = DivorceRecord.builder()
                .vitalEvent(savedEvent)
                .spouse1(spouse1)
                .spouse2(spouse2)
                .divorceDate(LocalDate.parse(request.getDivorceDate()))
                .courtName(request.getCourtName())
                .courtOrderNo(request.getCourtOrderNo())
                .build();
        divorceRecordRepository.save(divorceRecord);

        VitalEvent pendingEvent = vitalEventService.submitForApproval(savedEvent);

        log.info("Divorce registration submitted — vitalEventId: {}, spouse1: {}, spouse2: {}",
                pendingEvent.getId(), spouse1.getId(), spouse2.getId());

        return DivorceRegistrationResponse.builder()
                .vitalEventId(pendingEvent.getId())
                .status(pendingEvent.getStatus())
                .spouse1CitizenId(spouse1.getId())
                .spouse2CitizenId(spouse2.getId())
                .wardId(ward.getId())
                .submittedAt(pendingEvent.getSubmittedAt())
                .message("Divorce registration submitted for approval.")
                .build();
    }

    @Transactional
    public void approve(UUID vitalEventId, UUID approverId) {
        DivorceRecord divorceRecord = divorceRecordRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));

        Citizen spouse1 = divorceRecord.getSpouse1();
        Citizen spouse2 = divorceRecord.getSpouse2();

        // Final race-window backstop.
        checkMarriedToEachOther(spouse1, spouse2);

        vitalEventService.approve(vitalEventId, approverId);

        MaritalStatus previousStatus1 = spouse1.getMaritalStatus();
        MaritalStatus previousStatus2 = spouse2.getMaritalStatus();
        UUID ward1 = spouse1.getWard().getId();
        UUID ward2 = spouse2.getWard().getId();

        citizenService.updateMaritalStatus(spouse1.getId(), MaritalStatus.DIVORCED, null, approverId);
        citizenService.updateMaritalStatus(spouse2.getId(), MaritalStatus.DIVORCED, null, approverId);

        // No ward change — divorce never touches residency, so
        // previous/new ward are identical for both history rows.
        recordMaritalStatusHistory(spouse1.getId(), vitalEventId, previousStatus1,
                ward1, ward1, divorceRecord.getDivorceDate());
        recordMaritalStatusHistory(spouse2.getId(), vitalEventId, previousStatus2,
                ward2, ward2, divorceRecord.getDivorceDate());

        // Extended Modules §4.7 — one certificate per spouse, same
        // pattern as marriage.
        officialDocumentService.issueCertificateForVitalEvent(
                DocumentType.DIVORCE_CERTIFICATE, spouse1.getId(), vitalEventId, approverId);
        officialDocumentService.issueCertificateForVitalEvent(
                DocumentType.DIVORCE_CERTIFICATE, spouse2.getId(), vitalEventId, approverId);

        log.info("Divorce event approved — vitalEventId: {}, spouse1: {}, spouse2: {}",
                vitalEventId, spouse1.getId(), spouse2.getId());
    }

    @Transactional
    public void reject(UUID vitalEventId, UUID approverId, String reason) {
        vitalEventService.reject(vitalEventId, approverId, reason);
    }

    private void recordMaritalStatusHistory(UUID citizenId, UUID vitalEventId,
                                             MaritalStatus previousStatus,
                                             UUID previousWardId, UUID newWardId, LocalDate effectiveDate) {
        Ward previousWard = wardRepository.findById(previousWardId).orElse(null);
        Ward newWard = wardRepository.findById(newWardId).orElse(null);

        MaritalStatusHistory history = MaritalStatusHistory.builder()
                .citizenId(citizenId)
                .vitalEventId(vitalEventId)
                .previousMaritalStatus(previousStatus)
                .newMaritalStatus(MaritalStatus.DIVORCED)
                .previousWard(previousWard)
                .newWard(newWard)
                .effectiveDate(effectiveDate)
                .build();
        maritalStatusHistoryRepository.save(history);
    }

    private void checkMarriedToEachOther(Citizen spouse1, Citizen spouse2) {
        boolean married = spouse1.getMaritalStatus() == MaritalStatus.MARRIED
                && spouse2.getMaritalStatus() == MaritalStatus.MARRIED
                && spouse1.getId().equals(spouse2.getSpouseCitizenId())
                && spouse2.getId().equals(spouse1.getSpouseCitizenId());
        if (!married) {
            throw new NotMarriedToEachOtherException(
                    "Citizens " + spouse1.getId() + " and " + spouse2.getId()
                            + " are not currently married to each other.");
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
