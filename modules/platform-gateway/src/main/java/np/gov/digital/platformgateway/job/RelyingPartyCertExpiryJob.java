package np.gov.digital.platformgateway.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.entity.RelyingParty;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.repository.RelyingPartyRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * A relying party whose stored client certificate has lapsed loses ACTIVE
 * status automatically — GatewayVerificationService.verify() already
 * rejects a lapsed-but-still-ACTIVE certificate inline on every call, so
 * this daily sweep is a backstop that keeps the party's own status
 * column honest (visible on GET /admin/relying-parties/{id} and the
 * revoked/suspended listings) even between verify() calls, not the only
 * enforcement point.
 *
 * Same infrastructure decision, and same known audit-trail limitation,
 * as VitalEventAutoEscalationJob / EvidenceRequestExpiryJob — see those
 * jobs' own class Javadoc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RelyingPartyCertExpiryJob {

    private final RelyingPartyRepository relyingPartyRepository;
    private final AuditLogService auditLogService;

    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000L, initialDelay = 120 * 1000L)
    @Transactional
    public void suspendLapsedCertificates() {
        Instant now = Instant.now();
        var lapsed = relyingPartyRepository.findByStatusAndCertificateExpiresAtBefore(
                RelyingPartyStatus.ACTIVE, now);

        int suspended = 0;
        for (RelyingParty relyingParty : lapsed) {
            try {
                relyingParty.setStatus(RelyingPartyStatus.SUSPENDED);
                relyingParty.setSuspensionReason("Client certificate expired at " + relyingParty.getCertificateExpiresAt());
                relyingPartyRepository.save(relyingParty);

                auditLogService.log(AuditEventType.RELYING_PARTY_SUSPENDED, null,
                        "Relying party " + relyingParty.getId() + " auto-suspended — certificate expired");
                suspended++;
            } catch (Exception e) {
                log.error("RelyingPartyCertExpiryJob: failed to suspend relyingPartyId={}: {}",
                        relyingParty.getId(), e.getMessage(), e);
            }
        }

        if (suspended > 0) {
            log.info("RelyingPartyCertExpiryJob: auto-suspended {} of {} relying part(y/ies) with lapsed certificates",
                    suspended, lapsed.size());
        } else {
            log.debug("RelyingPartyCertExpiryJob: no relying parties with lapsed certificates ({} checked)", lapsed.size());
        }
    }
}
