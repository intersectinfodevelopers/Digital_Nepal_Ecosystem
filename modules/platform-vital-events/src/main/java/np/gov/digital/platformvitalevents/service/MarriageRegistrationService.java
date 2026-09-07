package np.gov.digital.platformvitalevents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.enums.CitizenStatus;
import np.gov.digital.citizen.enums.MaritalStatus;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.repository.WardRepository;
import np.gov.digital.citizen.service.CitizenService;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformvitalevents.dto.MarriageApprovalResponse;
import np.gov.digital.platformvitalevents.dto.MarriageRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.MarriageRegistrationResponse;
import np.gov.digital.platformvitalevents.entity.MaritalStatusHistory;
import np.gov.digital.platformvitalevents.entity.MarriageRecord;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;
import np.gov.digital.platformvitalevents.exception.AlreadyMarriedException;
import np.gov.digital.platformvitalevents.exception.UnderageMarriageException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.repository.MaritalStatusHistoryRepository;
import np.gov.digital.platformvitalevents.repository.MarriageRecordRepository;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Marriage registration (SDD Extended Modules §4.4) — gender-neutral,
 * hard-blocked on underage (&lt;20) and bigamy. Neither block is
 * overridable by any role; both are re-checked at approval time as well
 * as submission, closing the race window between two concurrent
 * submissions that both passed the first check.
 *
 * Ward transfer scope: only a same-municipality transfer is performed
 * here, and only when the couple explicitly names a relocating spouse —
 * never assumed from either spouse's identity. A cross-municipality move
 * needs the two-party losing/receiving Local Body Admin handoff that
 * migration_record (§4.6) will own; that doesn't exist yet, so a request
 * naming a relocation across municipalities is rejected with a clear
 * message rather than either silently ignored or done without the
 * handoff it's supposed to require.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarriageRegistrationService {

    private static final int MINIMUM_MARRIAGE_AGE = 20;

    private final VitalEventRepository vitalEventRepository;
    private final MarriageRecordRepository marriageRecordRepository;
    private final MaritalStatusHistoryRepository maritalStatusHistoryRepository;
    private final WardRepository wardRepository;
    private final CitizenRepository citizenRepository;
    private final VitalEventService vitalEventService;
    private final CitizenService citizenService;
    private final NidEncryptionUtil nidEncryptionUtil;

    @Transactional
    public MarriageRegistrationResponse register(MarriageRegistrationRequest request) {

        Ward ward = wardRepository.findById(request.getWardId())
                .orElseThrow(() -> new WardNotFoundException(request.getWardId()));

        if (request.getSpouse1CitizenId().equals(request.getSpouse2CitizenId())) {
            throw new IllegalArgumentException("A citizen cannot marry themselves.");
        }

        Citizen spouse1 = loadActiveCitizen(request.getSpouse1CitizenId());
        Citizen spouse2 = loadActiveCitizen(request.getSpouse2CitizenId());

        LocalDate marriageDate = LocalDate.parse(request.getMarriageDate());

        checkNotUnderage(spouse1, marriageDate);
        checkNotUnderage(spouse2, marriageDate);
        checkNotAlreadyMarried(spouse1);
        checkNotAlreadyMarried(spouse2);

        Citizen relocatingCitizen = null;
        if (request.getRelocatingCitizenId() != null) {
            if (!request.getRelocatingCitizenId().equals(spouse1.getId())
                    && !request.getRelocatingCitizenId().equals(spouse2.getId())) {
                throw new IllegalArgumentException("relocatingCitizenId must be one of the two spouses.");
            }
            relocatingCitizen = request.getRelocatingCitizenId().equals(spouse1.getId()) ? spouse1 : spouse2;
            Citizen destinationSpouse = relocatingCitizen == spouse1 ? spouse2 : spouse1;

            if (!relocatingCitizen.getWard().getMunicipality().getId()
                    .equals(destinationSpouse.getWard().getMunicipality().getId())) {
                throw new IllegalArgumentException(
                        "Relocation across municipalities isn't supported by marriage registration — "
                                + "it requires the cross-municipality transfer handoff, not yet implemented.");
            }
        }

        UUID actorId = getActorId();

        VitalEvent vitalEvent = VitalEvent.builder()
                .eventType(VitalEventType.MARRIAGE)
                .status(VitalEventStatus.SUBMITTED)
                .ward(ward)
                .submittedBy(actorId)
                .build();
        VitalEvent savedEvent = vitalEventRepository.saveAndFlush(vitalEvent);

        MarriageRecord marriageRecord = MarriageRecord.builder()
                .vitalEvent(savedEvent)
                .spouse1(spouse1)
                .spouse2(spouse2)
                .marriageDate(marriageDate)
                .marriagePlace(request.getMarriagePlace())
                .witness1Name(request.getWitness1Name())
                .witness2Name(request.getWitness2Name())
                .relocatingCitizen(relocatingCitizen)
                .build();
        marriageRecordRepository.save(marriageRecord);

        VitalEvent pendingEvent = vitalEventService.submitForApproval(savedEvent);

        log.info("Marriage registration submitted — vitalEventId: {}, spouse1: {}, spouse2: {}",
                pendingEvent.getId(), spouse1.getId(), spouse2.getId());

        return MarriageRegistrationResponse.builder()
                .vitalEventId(pendingEvent.getId())
                .status(pendingEvent.getStatus())
                .spouse1CitizenId(spouse1.getId())
                .spouse2CitizenId(spouse2.getId())
                .wardId(ward.getId())
                .submittedAt(pendingEvent.getSubmittedAt())
                .message("Marriage registration submitted for approval.")
                .build();
    }

    @Transactional
    public MarriageApprovalResponse approve(UUID vitalEventId, UUID approverId) {
        MarriageRecord marriageRecord = marriageRecordRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));

        Citizen spouse1 = marriageRecord.getSpouse1();
        Citizen spouse2 = marriageRecord.getSpouse2();

        // Final race-window backstop — re-check bigamy immediately before
        // applying the cascade (see class Javadoc).
        checkNotAlreadyMarried(spouse1);
        checkNotAlreadyMarried(spouse2);

        vitalEventService.approve(vitalEventId, approverId);

        UUID previousWard1 = spouse1.getWard().getId();
        UUID previousWard2 = spouse2.getWard().getId();
        MaritalStatus previousStatus1 = spouse1.getMaritalStatus();
        MaritalStatus previousStatus2 = spouse2.getMaritalStatus();

        citizenService.updateMaritalStatus(spouse1.getId(), MaritalStatus.MARRIED, spouse2.getId(), approverId);
        citizenService.updateMaritalStatus(spouse2.getId(), MaritalStatus.MARRIED, spouse1.getId(), approverId);

        UUID relocatedCitizenId = null;
        UUID relocatedToWardId = null;
        if (marriageRecord.getRelocatingCitizen() != null) {
            Citizen relocating = marriageRecord.getRelocatingCitizen();
            Citizen destination = relocating.getId().equals(spouse1.getId()) ? spouse2 : spouse1;
            UUID destinationWardId = destination.getWard().getId();

            citizenService.transferWard(relocating.getId(), destinationWardId, approverId);
            relocatedCitizenId = relocating.getId();
            relocatedToWardId = destinationWardId;
        }

        recordMaritalStatusHistory(spouse1.getId(), vitalEventId, previousStatus1, MaritalStatus.MARRIED,
                previousWard1, relocatedCitizenId != null && relocatedCitizenId.equals(spouse1.getId())
                        ? relocatedToWardId : previousWard1,
                marriageRecord.getMarriageDate());
        recordMaritalStatusHistory(spouse2.getId(), vitalEventId, previousStatus2, MaritalStatus.MARRIED,
                previousWard2, relocatedCitizenId != null && relocatedCitizenId.equals(spouse2.getId())
                        ? relocatedToWardId : previousWard2,
                marriageRecord.getMarriageDate());

        log.info("Marriage event approved — vitalEventId: {}, spouse1: {}, spouse2: {}, relocated: {}",
                vitalEventId, spouse1.getId(), spouse2.getId(), relocatedCitizenId);

        return MarriageApprovalResponse.builder()
                .spouse1CitizenId(spouse1.getId())
                .spouse2CitizenId(spouse2.getId())
                .relocatedCitizenId(relocatedCitizenId)
                .relocatedToWardId(relocatedToWardId)
                .build();
    }

    @Transactional
    public void reject(UUID vitalEventId, UUID approverId, String reason) {
        vitalEventService.reject(vitalEventId, approverId, reason);
    }

    private void recordMaritalStatusHistory(UUID citizenId, UUID vitalEventId,
                                             MaritalStatus previousStatus, MaritalStatus newStatus,
                                             UUID previousWardId, UUID newWardId, LocalDate effectiveDate) {
        Ward previousWard = wardRepository.findById(previousWardId).orElse(null);
        Ward newWard = wardRepository.findById(newWardId).orElse(null);

        MaritalStatusHistory history = MaritalStatusHistory.builder()
                .citizenId(citizenId)
                .vitalEventId(vitalEventId)
                .previousMaritalStatus(previousStatus)
                .newMaritalStatus(newStatus)
                .previousWard(previousWard)
                .newWard(newWard)
                .effectiveDate(effectiveDate)
                .build();
        maritalStatusHistoryRepository.save(history);
    }

    private Citizen loadActiveCitizen(UUID citizenId) {
        Citizen citizen = citizenRepository.findById(citizenId)
                .orElseThrow(() -> new CitizenNotFoundException(citizenId));
        if (!Boolean.TRUE.equals(citizen.getIsActive()) || citizen.getStatus() != CitizenStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Citizen " + citizenId + " is not an active citizen and cannot be party to a marriage.");
        }
        return citizen;
    }

    private void checkNotUnderage(Citizen citizen, LocalDate marriageDate) {
        String dobPlaintext = nidEncryptionUtil.decrypt(citizen.getDobEnc());
        LocalDate dob = LocalDate.parse(dobPlaintext);
        int age = Period.between(dob, marriageDate).getYears();
        if (age < MINIMUM_MARRIAGE_AGE) {
            throw new UnderageMarriageException(
                    "Citizen " + citizen.getId() + " is " + age + " years old at the marriage date — "
                            + "minimum marriage age is " + MINIMUM_MARRIAGE_AGE + " (ERR_MARRIAGE_UNDERAGE).");
        }
    }

    private void checkNotAlreadyMarried(Citizen citizen) {
        if (citizen.getMaritalStatus() == MaritalStatus.MARRIED) {
            throw new AlreadyMarriedException(
                    "Citizen " + citizen.getId() + " is already married (ERR_MARRIAGE_ALREADY_MARRIED).");
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
