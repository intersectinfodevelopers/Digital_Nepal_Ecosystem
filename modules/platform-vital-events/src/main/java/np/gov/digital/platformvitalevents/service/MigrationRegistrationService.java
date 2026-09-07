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
import np.gov.digital.platformvitalevents.dto.MigrationConfirmationResponse;
import np.gov.digital.platformvitalevents.dto.MigrationRegistrationRequest;
import np.gov.digital.platformvitalevents.dto.MigrationRegistrationResponse;
import np.gov.digital.platformvitalevents.entity.MigrationRecord;
import np.gov.digital.platformvitalevents.entity.VitalEvent;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;
import np.gov.digital.platformvitalevents.exception.SelfApprovalException;
import np.gov.digital.platformvitalevents.exception.TransferPendingException;
import np.gov.digital.platformvitalevents.exception.VitalEventNotFoundException;
import np.gov.digital.platformvitalevents.exception.WrongMunicipalityException;
import np.gov.digital.platformvitalevents.repository.MigrationRecordRepository;
import np.gov.digital.platformvitalevents.repository.VitalEventRepository;
import np.gov.digital.platformvitalevents.statemachine.VitalEventStateMachine;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-municipality ward transfer (SDD Extended Modules §4.6) — the one
 * vital event type with a genuine two-party approval, not a single
 * decision: the losing municipality's Local Body Admin and the receiving
 * municipality's Local Body Admin must each confirm independently before
 * the transfer happens. vital_event's own status/reviewedBy/reviewedAt
 * (built for a single approver) isn't used for the two confirmations
 * themselves — see MigrationRecord's Javadoc — it only moves to APPROVED
 * once both sides have confirmed, applied by whichever of the two
 * confirmations happens second.
 *
 * Same-municipality ward moves are out of scope here — MarriageRegistrationService
 * already covers that case as part of a marriage; a bare same-municipality
 * transfer with no vital event backing it at all isn't built by any
 * service yet, tracked as a known gap rather than silently expanded into
 * this one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationRegistrationService {

    private final VitalEventRepository vitalEventRepository;
    private final MigrationRecordRepository migrationRecordRepository;
    private final WardRepository wardRepository;
    private final CitizenRepository citizenRepository;
    private final VitalEventService vitalEventService;
    private final CitizenService citizenService;

    @Transactional
    public MigrationRegistrationResponse register(MigrationRegistrationRequest request) {

        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getCitizenId()));

        Ward fromWard = citizen.getWard();
        Ward toWard = wardRepository.findById(request.getToWardId())
                .orElseThrow(() -> new WardNotFoundException(request.getToWardId()));

        if (fromWard.getId().equals(toWard.getId())) {
            throw new IllegalArgumentException("Citizen is already registered in the destination ward.");
        }
        if (fromWard.getMunicipality().getId().equals(toWard.getMunicipality().getId())) {
            throw new IllegalArgumentException(
                    "Source and destination wards are in the same municipality — migration_record is for "
                            + "cross-municipality transfers only; a same-municipality move as part of a "
                            + "marriage is handled by the marriage registration endpoint instead.");
        }

        List<MigrationRecord> pending = migrationRecordRepository.findPendingByCitizenId(citizen.getId());
        if (!pending.isEmpty()) {
            throw new TransferPendingException(
                    "Citizen " + citizen.getId() + " already has a migration request in progress (ERR_TRANSFER_PENDING).");
        }

        UUID actorId = getActorId();

        VitalEvent vitalEvent = VitalEvent.builder()
                .eventType(VitalEventType.MIGRATION)
                .status(VitalEventStatus.SUBMITTED)
                .ward(fromWard)
                .submittedBy(actorId)
                .build();
        VitalEvent savedEvent = vitalEventRepository.saveAndFlush(vitalEvent);

        MigrationRecord migrationRecord = MigrationRecord.builder()
                .vitalEvent(savedEvent)
                .citizen(citizen)
                .fromWard(fromWard)
                .toWard(toWard)
                .reason(request.getReason())
                .build();
        migrationRecordRepository.save(migrationRecord);

        VitalEvent pendingEvent = vitalEventService.submitForApproval(savedEvent);

        log.info("Migration registration submitted — vitalEventId: {}, citizenId: {}, from: {}, to: {}",
                pendingEvent.getId(), citizen.getId(), fromWard.getId(), toWard.getId());

        return MigrationRegistrationResponse.builder()
                .vitalEventId(pendingEvent.getId())
                .status(pendingEvent.getStatus())
                .citizenId(citizen.getId())
                .fromWardId(fromWard.getId())
                .toWardId(toWard.getId())
                .submittedAt(pendingEvent.getSubmittedAt())
                .message("Migration request submitted — awaiting confirmation from both municipalities.")
                .build();
    }

    @Transactional
    public MigrationConfirmationResponse confirmLosing(UUID vitalEventId, UUID actorId) {
        MigrationRecord record = getRecordAndCheckConfirmable(vitalEventId, actorId);

        requireActorInMunicipality(actorId, record.getFromWard().getMunicipality().getId(), "losing");

        if (record.getLosingAdminConfirmedAt() == null) {
            record.setLosingAdminConfirmedAt(Instant.now());
            record.setLosingAdminConfirmedBy(actorId);
            migrationRecordRepository.save(record);
            log.info("Migration losing-side confirmed — vitalEventId: {}, by: {}", vitalEventId, actorId);
        }

        return finalizeIfFullyConfirmed(record);
    }

    @Transactional
    public MigrationConfirmationResponse confirmReceiving(UUID vitalEventId, UUID actorId) {
        MigrationRecord record = getRecordAndCheckConfirmable(vitalEventId, actorId);

        requireActorInMunicipality(actorId, record.getToWard().getMunicipality().getId(), "receiving");

        if (record.getReceivingAdminConfirmedAt() == null) {
            record.setReceivingAdminConfirmedAt(Instant.now());
            record.setReceivingAdminConfirmedBy(actorId);
            migrationRecordRepository.save(record);
            log.info("Migration receiving-side confirmed — vitalEventId: {}, by: {}", vitalEventId, actorId);
        }

        return finalizeIfFullyConfirmed(record);
    }

    @Transactional
    public void reject(UUID vitalEventId, UUID actorId, String reason) {
        MigrationRecord record = migrationRecordRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));

        UUID losingMunicipality = record.getFromWard().getMunicipality().getId();
        UUID receivingMunicipality = record.getToWard().getMunicipality().getId();
        UUID actorMunicipality = getActorMunicipality(actorId);
        if (!losingMunicipality.equals(actorMunicipality) && !receivingMunicipality.equals(actorMunicipality)) {
            throw new WrongMunicipalityException(
                    "Only a Local Body Admin of the losing or receiving municipality may reject this migration.");
        }

        vitalEventService.reject(vitalEventId, actorId, reason);
    }

    private MigrationConfirmationResponse finalizeIfFullyConfirmed(MigrationRecord record) {
        if (record.isFullyConfirmed()) {
            // Whichever confirmation completed second is recorded as the
            // vital_event's own "approver" — a real, non-arbitrary choice
            // since a two-party decision only becomes final at that moment.
            UUID finalizingActor = record.getLosingAdminConfirmedAt().isAfter(record.getReceivingAdminConfirmedAt())
                    ? record.getLosingAdminConfirmedBy()
                    : record.getReceivingAdminConfirmedBy();

            vitalEventService.approve(record.getVitalEventId(), finalizingActor);
            citizenService.transferWard(record.getCitizen().getId(), record.getToWard().getId(), finalizingActor);

            log.info("Migration fully confirmed — vitalEventId: {}, citizenId: {} transferred to ward {}",
                    record.getVitalEventId(), record.getCitizen().getId(), record.getToWard().getId());
        }

        return MigrationConfirmationResponse.builder()
                .vitalEventId(record.getVitalEventId())
                .losingAdminConfirmed(record.getLosingAdminConfirmedAt() != null)
                .receivingAdminConfirmed(record.getReceivingAdminConfirmedAt() != null)
                .transferCompleted(record.isFullyConfirmed())
                .build();
    }

    private MigrationRecord getRecordAndCheckConfirmable(UUID vitalEventId, UUID actorId) {
        MigrationRecord record = migrationRecordRepository.findById(vitalEventId)
                .orElseThrow(() -> new VitalEventNotFoundException(vitalEventId));

        VitalEventStatus status = record.getVitalEvent().getStatus();
        if (status != VitalEventStatus.PENDING_APPROVAL && status != VitalEventStatus.CAO_REVIEW) {
            VitalEventStateMachine.validate(status, VitalEventStatus.APPROVED); // always throws here, with a clear message
        }

        // No-self-decision (Governance Tiers §3) applies to each individual
        // confirmation, not just the finalizing one — a two-party decision
        // is only genuinely independent if neither confirming party is
        // also the one who submitted the request.
        if (actorId.equals(record.getVitalEvent().getSubmittedBy())) {
            throw new SelfApprovalException(
                    "Cannot confirm a migration you submitted yourself.");
        }

        return record;
    }

    private void requireActorInMunicipality(UUID actorId, UUID requiredMunicipalityId, String side) {
        UUID actorMunicipality = getActorMunicipality(actorId);
        if (!requiredMunicipalityId.equals(actorMunicipality)) {
            throw new WrongMunicipalityException(
                    "The " + side + "-side confirmation must come from a Local Body Admin of that specific municipality.");
        }
    }

    private UUID getActorMunicipality(UUID actorId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedActor actor
                    && actor.getUserId().equals(actorId)) {
                return actor.getMunicipalityId();
            }
        } catch (Exception e) {
            log.warn("Could not extract actor municipality from SecurityContext", e);
        }
        return null;
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
