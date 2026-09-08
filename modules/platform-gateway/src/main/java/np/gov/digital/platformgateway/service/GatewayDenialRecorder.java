package np.gov.digital.platformgateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.entity.GatewayAccessLog;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.enums.AccessOutcome;
import np.gov.digital.platformgateway.enums.ConsentMethod;
import np.gov.digital.platformgateway.enums.PurposeStatus;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.repository.GatewayAccessLogRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyPurposeRepository;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records the side effects of a denied verify() call — access log entry,
 * the real-time anomaly counter, and the audit event — in their OWN
 * transaction, committed independently of the caller.
 *
 * Found live, the hard way: GatewayVerificationService.verify() is
 * @Transactional, and every denial path used to both persist these same
 * writes AND throw the denial exception from within that same method.
 * Spring's default rollback-on-unchecked-exception then undid every one
 * of those writes the instant the exception left the method — the
 * gateway_access_log transparency record never actually recorded a
 * single denial, and consecutiveDeniedCount never actually incremented,
 * so the graduated penalty ladder (§6.5) silently never fired no matter
 * how many times a relying party was denied. Confirmed via a real
 * sequence of denied verify() calls against a live database: the
 * counter and the log rows simply never appeared. REQUIRES_NEW here
 * means these writes commit before verify() ever throws, so they
 * survive regardless of what the caller's own transaction does next.
 *
 * Re-fetches citizen/relying party fresh by ID rather than accepting the
 * caller's already-loaded entities, since those belong to a different
 * (about-to-roll-back) persistence context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayDenialRecorder {

    private static final int DENIALS_BEFORE_PURPOSE_SUSPENDED = 5;
    private static final int DENIALS_BEFORE_PARTY_SUSPENDED = 10;

    private final CitizenRepository citizenRepository;
    private final RelyingPartyRepository relyingPartyRepository;
    private final RelyingPartyPurposeRepository relyingPartyPurposeRepository;
    private final GatewayAccessLogRepository gatewayAccessLogRepository;
    private final AuditLogService auditLogService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenial(UUID citizenId, UUID relyingPartyId, String purposeCode,
                              AccessOutcome outcome, ConsentMethod consentMethod, String message) {
        Citizen citizen = citizenRepository.findById(citizenId).orElse(null);
        RelyingParty relyingParty = relyingPartyRepository.findById(relyingPartyId).orElse(null);
        if (citizen == null || relyingParty == null) {
            // Both were just loaded moments ago in the caller's own
            // transaction — this would mean one was deleted mid-request.
            // Nothing sensible to record against; log and move on rather
            // than fail the recorder itself.
            log.error("GatewayDenialRecorder: citizen {} or relying party {} vanished before denial could be recorded",
                    citizenId, relyingPartyId);
            return;
        }

        GatewayAccessLog accessLog = GatewayAccessLog.builder()
                .citizen(citizen)
                .relyingPartyId(relyingPartyId)
                .purposeCode(purposeCode)
                .consentMethod(consentMethod)
                .outcome(outcome)
                .build();
        gatewayAccessLogRepository.save(accessLog);

        auditLogService.log(AuditEventType.GATEWAY_VERIFY_DENIED, citizenId,
                relyingParty.getName() + " denied (" + outcome + ") for " + purposeCode + ": " + message);

        applyAnomalyResponse(relyingParty, purposeCode);
    }

    /** Real-time graduated penalty ladder (§6.5) — see GatewayVerificationService's class Javadoc. */
    private void applyAnomalyResponse(RelyingParty relyingParty, String purposeCode) {
        int denials = relyingParty.getConsecutiveDeniedCount() + 1;
        relyingParty.setConsecutiveDeniedCount(denials);

        if (denials == DENIALS_BEFORE_PURPOSE_SUSPENDED) {
            relyingPartyPurposeRepository.findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), purposeCode)
                    .ifPresent(p -> {
                        p.setStatus(PurposeStatus.SUSPENDED);
                        relyingPartyPurposeRepository.save(p);
                        log.warn("Gateway anomaly ladder: suspended purpose {} for relying party {} after {} consecutive denials",
                                purposeCode, relyingParty.getId(), denials);
                    });
        } else if (denials >= DENIALS_BEFORE_PARTY_SUSPENDED) {
            relyingParty.setStatus(RelyingPartyStatus.SUSPENDED);
            relyingParty.setSuspensionReason(
                    "Auto-suspended by GatewayVerificationService after " + denials + " consecutive denied verify calls.");
            auditLogService.log(AuditEventType.RELYING_PARTY_SUSPENDED, null,
                    "Relying party " + relyingParty.getId() + " auto-suspended after " + denials + " consecutive denials");
            log.warn("Gateway anomaly ladder: SUSPENDED relying party {} after {} consecutive denials",
                    relyingParty.getId(), denials);
        }

        relyingPartyRepository.save(relyingParty);
    }
}
