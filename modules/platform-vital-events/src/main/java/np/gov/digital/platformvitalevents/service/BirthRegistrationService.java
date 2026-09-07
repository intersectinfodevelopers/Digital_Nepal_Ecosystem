package np.gov.digital.platformvitalevents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.repository.WardRepository;
import np.gov.digital.citizen.service.CitizenService;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformvitalevents.dto.BirthRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.BirthRegistrationResponse;
import np.gov.digital.platformvitalevents.entity.BirthRecord;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.repository.BirthRecordRepository;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirthRegistrationService {

    private final VitalEventRepository vitalEventRepository;
    private final BirthRecordRepository birthRecordRepository;
    private final WardRepository wardRepository;
    private final CitizenRepository citizenRepository;
    private final VitalEventService vitalEventService;
    private final CitizenService citizenService;

    @Transactional
    public BirthRegistrationResponse register(BirthRegistrationRequest request) {

        Ward ward = wardRepository.findById(request.getWardId())
                .orElseThrow(() -> new WardNotFoundException(request.getWardId()));

        // Each parent must be identified one way or the other — a real
        // citizen record, or (very common for a first registration) just a
        // name on file. Mirrors chk_birth_record_father_identified /
        // chk_birth_record_mother_identified at the DB level, but surfaces
        // a clean 400 here instead of a raw constraint-violation 500.
        if (request.getFatherCitizenId() == null && isBlank(request.getFatherNameText())) {
            throw new IllegalArgumentException(
                    "Either fatherCitizenId or fatherNameText is required.");
        }
        if (request.getMotherCitizenId() == null && isBlank(request.getMotherNameText())) {
            throw new IllegalArgumentException(
                    "Either motherCitizenId or motherNameText is required.");
        }

        Citizen fatherCitizen = request.getFatherCitizenId() != null
                ? citizenRepository.findById(request.getFatherCitizenId())
                        .orElseThrow(() -> new CitizenNotFoundException(request.getFatherCitizenId()))
                : null;
        Citizen motherCitizen = request.getMotherCitizenId() != null
                ? citizenRepository.findById(request.getMotherCitizenId())
                        .orElseThrow(() -> new CitizenNotFoundException(request.getMotherCitizenId()))
                : null;

        LocalDate dob = LocalDate.parse(request.getDateOfBirth());
        UUID actorId = getActorId();

        VitalEvent vitalEvent = VitalEvent.builder()
                .eventType(VitalEventType.BIRTH)
                .status(VitalEventStatus.SUBMITTED)
                .ward(ward)
                .submittedBy(actorId)
                .build();
        VitalEvent savedEvent = vitalEventRepository.saveAndFlush(vitalEvent);

        BirthRecord birthRecord = BirthRecord.builder()
                .vitalEvent(savedEvent)
                .childNameNp(request.getChildNameNp())
                .childNameEn(request.getChildNameEn())
                .sex(request.getSex())
                .dateOfBirth(dob)
                .placeOfBirth(request.getPlaceOfBirth())
                .fatherCitizen(fatherCitizen)
                .fatherNameText(request.getFatherNameText())
                .motherCitizen(motherCitizen)
                .motherNameText(request.getMotherNameText())
                .birthWeightKg(request.getBirthWeightKg())
                .deliveryType(request.getDeliveryType())
                .attendingFacility(request.getAttendingFacility())
                .build();
        birthRecordRepository.save(birthRecord);

        // Fuzzy duplicate flag — informational, never blocks submission
        // (see BirthRecordRepository.findPossibleDuplicates's Javadoc).
        List<UUID> possibleDuplicates = birthRecordRepository.findPossibleDuplicates(
                        ward.getId(), request.getChildNameEn(), dob).stream()
                .map(BirthRecord::getVitalEventId)
                .filter(id -> !id.equals(savedEvent.getId()))
                .collect(Collectors.toList());

        VitalEvent pendingEvent = vitalEventService.submitForApproval(savedEvent);

        log.info("Birth registration submitted — vitalEventId: {}, ward: {}, possibleDuplicates: {}",
                pendingEvent.getId(), ward.getId(), possibleDuplicates.size());

        return BirthRegistrationResponse.builder()
                .vitalEventId(pendingEvent.getId())
                .status(pendingEvent.getStatus())
                .childNameEn(request.getChildNameEn())
                .wardId(ward.getId())
                .submittedAt(pendingEvent.getSubmittedAt())
                .possibleDuplicateVitalEventIds(possibleDuplicates)
                .message(possibleDuplicates.isEmpty()
                        ? "Birth registration submitted for approval."
                        : "Birth registration submitted for approval. "
                                + possibleDuplicates.size() + " possible duplicate(s) flagged for review.")
                .build();
    }

    /**
     * Approves a birth event and creates the resulting citizen record
     * (SDD Extended Modules §4.2) — registrationStage BIRTH_REGISTERED,
     * no NID/citizenship yet. birthRegistrationNo is a simple, readable
     * tracking number; it is NOT a legal document number, just a way to
     * reference this specific registration later.
     */
    @Transactional
    public UUID approve(UUID vitalEventId, UUID approverId) {
        BirthRecord birthRecord = birthRecordRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));

        VitalEvent approved = vitalEventService.approve(vitalEventId, approverId);

        String birthRegistrationNo = "BR-" + approved.getWard().getId().toString().substring(0, 8).toUpperCase()
                + "-" + approved.getId().toString().substring(0, 8).toUpperCase();

        Citizen child = citizenService.registerNewbornFromBirthEvent(
                approved.getWard().getId(),
                birthRecord.getChildNameNp(),
                birthRecord.getChildNameEn(),
                birthRecord.getSex(),
                birthRecord.getDateOfBirth().toString(),
                birthRegistrationNo,
                approverId
        );

        birthRecord.setChildCitizen(child);
        birthRecordRepository.save(birthRecord);

        log.info("Birth event approved — vitalEventId: {}, new citizenId: {}", vitalEventId, child.getId());
        return child.getId();
    }

    @Transactional
    public void reject(UUID vitalEventId, UUID approverId, String reason) {
        vitalEventService.reject(vitalEventId, approverId, reason);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
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
