package np.gov.digital.platformbenefits.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.EligibilityResponse;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.enums.IdCardType;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.service.EligibilityService;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformbenefits.dto.DisburseRequest;
import np.gov.digital.platformbenefits.dto.DisbursementCallbackRequest;
import np.gov.digital.platformbenefits.dto.DisbursementResponse;
import np.gov.digital.platformbenefits.dto.EligibleCitizenResponse;
import np.gov.digital.platformbenefits.entity.BenefitDisbursement;
import np.gov.digital.platformbenefits.enums.BenefitType;
import np.gov.digital.platformbenefits.enums.PaymentStatus;
import np.gov.digital.platformbenefits.exception.DisbursementNotFoundException;
import np.gov.digital.platformbenefits.exception.DuplicateDisbursementException;
import np.gov.digital.platformbenefits.exception.IneligibleCitizenException;
import np.gov.digital.platformbenefits.repository.BenefitDisbursementRepository;
import np.gov.digital.platformbenefits.statemachine.PaymentStateMachine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Government-to-Person cash disbursement (Governance Tiers §8). See
 * V39's migration comment for what's genuinely new here versus what the
 * plan's phrasing assumed already existed.
 *
 * KNOWN LIMITATION, deliberate: the callback endpoint
 * (attachCallback()/DisbursementCallbackController) authenticates via a
 * shared secret header, not the mTLS the design doc calls for elsewhere
 * for externally-reachable callbacks (evidence-exchange's). There is no
 * real payment-rail integration anywhere to hold an actual mTLS
 * certificate against — eSewa/Khalti/ConnectIPS/bank-transfer are none
 * of them wired up, and the "Data residency" decision the plan's own
 * Concept Document §15 lists as gating Phase 3 (national vs. commercial
 * cloud) determines what a real integration would even look like.
 * Building fake mTLS against no real counterparty would be security
 * theater, not a stronger guarantee — the shared secret is a genuine,
 * if weaker, stand-in until a real payment rail is selected and
 * integrated for real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BenefitDisbursementService {

    private final BenefitDisbursementRepository benefitDisbursementRepository;
    private final CitizenRepository citizenRepository;
    private final EligibilityService eligibilityService;
    private final AuditLogService auditLogService;

    @Transactional
    public DisbursementResponse disburse(UUID citizenId, DisburseRequest request) {
        Citizen citizen = citizenRepository.findById(citizenId)
                .orElseThrow(() -> new CitizenNotFoundException(citizenId));

        checkEligible(citizenId, request.getBenefitType());
        checkNoActiveDisbursement(citizenId, request.getBenefitType(), request.getPeriod());

        UUID actorId = getActorId();

        BenefitDisbursement disbursement = BenefitDisbursement.builder()
                .citizen(citizen)
                .benefitType(request.getBenefitType())
                .period(request.getPeriod())
                .amountNpr(request.getAmountNpr())
                .paymentRail(request.getPaymentRail())
                .paymentStatus(PaymentStatus.PENDING)
                .initiatedBy(actorId)
                .build();

        // PENDING -> INITIATED happens immediately — "disburse" means the
        // payment has actually been handed to the rail, not just queued.
        // A real integration would call the rail's API here; there is
        // none to call (see class Javadoc), so this generates our own
        // correlation reference the (future) real rail's settlement
        // callback would be matched against.
        PaymentStateMachine.validate(disbursement.getPaymentStatus(), PaymentStatus.INITIATED);
        disbursement.setPaymentStatus(PaymentStatus.INITIATED);
        disbursement.setExternalReference("DISB-" + UUID.randomUUID());

        BenefitDisbursement saved = benefitDisbursementRepository.save(disbursement);

        auditLogService.log(AuditEventType.BENEFIT_DISBURSEMENT_INITIATED, citizenId,
                request.getBenefitType() + " disbursement initiated for period " + request.getPeriod()
                        + " via " + request.getPaymentRail());

        log.info("Disbursement initiated — id: {}, citizen: {}, benefitType: {}, period: {}",
                saved.getId(), citizenId, request.getBenefitType(), request.getPeriod());

        return toResponse(saved);
    }

    // KNOWN LIMITATION, confirmed live, same root cause as
    // VitalEventAutoEscalationJob's documented gap: this endpoint is
    // deliberately unauthenticated (a real payment gateway has no
    // citizen/admin JWT to present — see SecurityConfig's permitAll
    // entry for it), so AuditLogService.log() finds no actor on the
    // SecurityContext and silently skips writing to citizen_events (by
    // design — see its own class Javadoc). BENEFIT_DISBURSEMENT_SETTLED/
    // _FAILED events are therefore NOT currently in the audit trail,
    // confirmed by triggering a real callback end-to-end. The
    // disbursement row's own paymentStatus/settledAt are still a
    // durable, verifiable record of what happened and when — the same
    // gap, same fix (a reserved system-service account), tracked as one
    // follow-up rather than two.
    @Transactional
    public DisbursementResponse applyCallback(UUID disbursementId, DisbursementCallbackRequest callback) {
        BenefitDisbursement disbursement = benefitDisbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new DisbursementNotFoundException(disbursementId));

        PaymentStatus targetStatus = Boolean.TRUE.equals(callback.getSuccess())
                ? PaymentStatus.SETTLED : PaymentStatus.FAILED;
        PaymentStateMachine.validate(disbursement.getPaymentStatus(), targetStatus);

        disbursement.setPaymentStatus(targetStatus);
        if (targetStatus == PaymentStatus.SETTLED) {
            disbursement.setSettledAt(java.time.Instant.now());
        } else {
            disbursement.setFailureReason(callback.getFailureReason() != null
                    ? callback.getFailureReason() : "Payment rail reported failure with no reason given");
        }

        BenefitDisbursement saved = benefitDisbursementRepository.save(disbursement);

        auditLogService.log(
                targetStatus == PaymentStatus.SETTLED
                        ? AuditEventType.BENEFIT_DISBURSEMENT_SETTLED
                        : AuditEventType.BENEFIT_DISBURSEMENT_FAILED,
                disbursement.getCitizen().getId(),
                "Disbursement " + disbursementId + " " + targetStatus);

        log.info("Disbursement callback applied — id: {}, status: {}", disbursementId, targetStatus);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DisbursementResponse getStatus(UUID disbursementId) {
        return toResponse(benefitDisbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new DisbursementNotFoundException(disbursementId)));
    }

    @Transactional(readOnly = true)
    public Page<DisbursementResponse> listByCitizen(UUID citizenId, Pageable pageable) {
        return benefitDisbursementRepository.findByCitizen_Id(citizenId, pageable)
                .map(this::toResponse);
    }

    /**
     * Ward-scoped eligible-list. Deliberately simple, non-bulk-optimized
     * — re-evaluates each citizen in the page individually via the
     * existing EligibilityService, same granularity that service already
     * offers everywhere else in this codebase; there is no bulk/batch
     * eligibility query anywhere to build on top of instead. Paginated
     * at the citizen-fetch level (same as CitizenService.listByWard) so
     * a large ward doesn't force evaluating its entire population in one
     * request.
     */
    @Transactional(readOnly = true)
    public List<EligibleCitizenResponse> eligibleList(
            UUID wardId, BenefitType benefitType, String period, Pageable pageable) {

        List<EligibleCitizenResponse> results = new ArrayList<>();
        for (Citizen citizen : citizenRepository.findByWardIdAndIsActiveTrue(wardId, pageable)) {
            EligibilityResponse eligibility = eligibilityService.evaluate(citizen.getId());
            boolean eligible = eligibility.getEligibleCards().stream()
                    .anyMatch(r -> r.isEligible() && r.getCardType().name().equals(benefitType.name()));
            if (!eligible) continue;

            boolean alreadyDisbursed = !benefitDisbursementRepository
                    .findByCitizen_IdAndBenefitTypeAndPeriodAndPaymentStatusNotIn(
                            citizen.getId(), benefitType, period,
                            List.of(PaymentStatus.FAILED, PaymentStatus.CANCELLED))
                    .isEmpty();
            if (alreadyDisbursed) continue;

            String reason = eligibility.getEligibleCards().stream()
                    .filter(r -> r.getCardType().name().equals(benefitType.name()))
                    .findFirst().map(EligibilityResponse.EligibilityResult::getReason).orElse(null);

            results.add(EligibleCitizenResponse.builder()
                    .citizenId(citizen.getId())
                    .nameEn(citizen.getNameEn())
                    .benefitType(benefitType)
                    .eligibilityReason(reason)
                    .build());
        }
        return results;
    }

    private void checkEligible(UUID citizenId, BenefitType benefitType) {
        EligibilityResponse eligibility = eligibilityService.evaluate(citizenId);
        boolean eligible = eligibility.getEligibleCards().stream()
                .anyMatch(r -> r.isEligible() && r.getCardType() == IdCardType.valueOf(benefitType.name()));
        if (!eligible) {
            throw new IneligibleCitizenException(
                    "Citizen " + citizenId + " is not currently eligible for " + benefitType);
        }
    }

    private void checkNoActiveDisbursement(UUID citizenId, BenefitType benefitType, String period) {
        List<BenefitDisbursement> active = benefitDisbursementRepository
                .findByCitizen_IdAndBenefitTypeAndPeriodAndPaymentStatusNotIn(
                        citizenId, benefitType, period,
                        List.of(PaymentStatus.FAILED, PaymentStatus.CANCELLED));
        if (!active.isEmpty()) {
            throw new DuplicateDisbursementException(
                    "Citizen " + citizenId + " already has a " + benefitType
                            + " disbursement for period " + period + " (ERR_DUPLICATE_DISBURSEMENT).");
        }
    }

    private DisbursementResponse toResponse(BenefitDisbursement d) {
        return DisbursementResponse.builder()
                .id(d.getId())
                .citizenId(d.getCitizen().getId())
                .benefitType(d.getBenefitType())
                .period(d.getPeriod())
                .amountNpr(d.getAmountNpr())
                .paymentRail(d.getPaymentRail())
                .paymentStatus(d.getPaymentStatus())
                .externalReference(d.getExternalReference())
                .failureReason(d.getFailureReason())
                .initiatedAt(d.getInitiatedAt())
                .settledAt(d.getSettledAt())
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
