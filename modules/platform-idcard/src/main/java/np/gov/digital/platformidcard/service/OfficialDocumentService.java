package np.gov.digital.platformidcard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformidcard.entity.OfficialDocument;
import np.gov.digital.platformidcard.enums.DocumentStatus;
import np.gov.digital.platformidcard.enums.DocumentType;
import np.gov.digital.platformidcard.exception.InvalidDocumentStateException;
import np.gov.digital.platformidcard.exception.OfficialDocumentNotFoundException;
import np.gov.digital.platformidcard.repository.OfficialDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * A single persisted document/certificate table shared by ID cards and
 * vital-event certificates (SDD Extended Modules §4.7). Replaces
 * IdCardController's hardcoded placeholder logic — see this class's
 * package's OfficialDocument entity and V38's migration for why "renamed
 * from id_card" doesn't describe what actually happened here (there was
 * no id_card table to rename).
 *
 * ID cards go through the two-step initiate() -&gt; approveAndIssue()
 * flow IdCardController already exposed. Vital-event certificates skip
 * straight to issueCertificateForVitalEvent() — the underlying event's
 * own approval (by a Local Body Admin, already recorded on vital_event)
 * IS the authorization; a certificate doesn't need a second, separate
 * review.
 *
 * KNOWN LIMITATION, inherited unchanged from the ID-card verification
 * flow this replaces: QrCodeService's signed token carries
 * (citizenId, documentType, issuedDate) — not this row's own id. If a
 * citizen is somehow issued two documents of the same type on the same
 * day (e.g. a corrected re-issue), the token can't distinguish between
 * them by construction; verify() resolves to the most recently issued
 * match. Not a new gap introduced here — the pre-existing ID-card token
 * format was never id-based either — but now that verification also
 * checks document status (see verify()'s own note), it's worth flagging
 * plainly rather than carrying forward silently.
 *
 * ANOTHER LIVE-FOUND BUG, now fixed throughout this class: every public
 * method here used to return an OfficialDocument whose citizen (and that
 * citizen's own ward) are Hibernate LAZY proxies. Every caller
 * (IdCardController, the vital-event services) reads those fields from
 * outside this service's own @Transactional boundary — for PDF
 * generation or building a verify response — which threw
 * LazyInitializationException ("no Session") the moment anything touched
 * them. Confirmed live: approving an ID card failed exactly this way.
 * Fixed by eagerly resolving citizen+ward (initializeCitizenWard())
 * before any method here returns, and by making OfficialDocument.citizen
 * itself EAGER (see that entity).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialDocumentService {

    private static final int ID_CARD_VALIDITY_YEARS = 3;

    private final OfficialDocumentRepository officialDocumentRepository;
    private final CitizenRepository citizenRepository;
    private final QrCodeService qrCodeService;
    private final AuditLogService auditLogService;

    @Transactional
    public OfficialDocument initiate(DocumentType documentType, UUID citizenId, UUID actorId) {
        Citizen citizen = citizenRepository.findById(citizenId)
                .orElseThrow(() -> new CitizenNotFoundException(citizenId));

        OfficialDocument document = OfficialDocument.builder()
                .documentType(documentType)
                .citizen(citizen)
                .status(DocumentStatus.PRINT_PENDING)
                .initiatedBy(actorId)
                .build();
        OfficialDocument saved = officialDocumentRepository.save(document);

        auditLogService.log(AuditEventType.ID_CARD_INITIATED, citizenId,
                documentType + " document initiated");

        log.info("OfficialDocumentService: initiated {} for citizen={}", documentType, citizenId);
        initializeCitizenWard(saved);
        return saved;
    }

    @Transactional
    public OfficialDocument approveAndIssue(UUID documentId, UUID approverId) {
        OfficialDocument document = officialDocumentRepository.findById(documentId)
                .orElseThrow(() -> new OfficialDocumentNotFoundException(documentId));

        if (document.getStatus() != DocumentStatus.PRINT_PENDING) {
            throw new InvalidDocumentStateException(document.getStatus(), "approve");
        }

        issue(document, approverId);
        officialDocumentRepository.save(document);

        auditLogService.log(AuditEventType.ID_CARD_APPROVED, document.getCitizen().getId(),
                document.getDocumentType() + " document approved and issued");

        log.info("OfficialDocumentService: issued documentId={} type={} citizen={}",
                documentId, document.getDocumentType(), document.getCitizen().getId());
        initializeCitizenWard(document);
        return document;
    }

    /**
     * Certificates skip the separate approval step — see class Javadoc.
     * Creates and issues in one transaction.
     */
    @Transactional
    public OfficialDocument issueCertificateForVitalEvent(
            DocumentType documentType, UUID citizenId, UUID vitalEventId, UUID actorId) {

        if (!documentType.isCertificate()) {
            throw new IllegalArgumentException(documentType + " is not a certificate type.");
        }

        Citizen citizen = citizenRepository.findById(citizenId)
                .orElseThrow(() -> new CitizenNotFoundException(citizenId));

        OfficialDocument document = OfficialDocument.builder()
                .documentType(documentType)
                .citizen(citizen)
                .vitalEventId(vitalEventId)
                .status(DocumentStatus.PRINT_PENDING)
                .initiatedBy(actorId)
                .build();

        issue(document, actorId);
        OfficialDocument saved = officialDocumentRepository.save(document);

        auditLogService.log(AuditEventType.ID_CARD_APPROVED, citizenId,
                documentType + " issued for vital event " + vitalEventId);

        log.info("OfficialDocumentService: issued certificate type={} citizen={} vitalEventId={}",
                documentType, citizenId, vitalEventId);
        initializeCitizenWard(saved);
        return saved;
    }

    @Transactional
    public OfficialDocument revoke(UUID documentId, UUID actorId, String reason) {
        OfficialDocument document = officialDocumentRepository.findById(documentId)
                .orElseThrow(() -> new OfficialDocumentNotFoundException(documentId));

        if (document.getStatus() != DocumentStatus.ISSUED) {
            throw new InvalidDocumentStateException(document.getStatus(), "revoke");
        }

        document.setStatus(DocumentStatus.REVOKED);
        document.setRevokedAt(Instant.now());
        document.setRevokedBy(actorId);
        document.setRevocationReason(reason);
        officialDocumentRepository.save(document);

        auditLogService.log(AuditEventType.ID_CARD_REVOKED, document.getCitizen().getId(),
                document.getDocumentType() + " document revoked: " + reason);

        log.info("OfficialDocumentService: revoked documentId={}", documentId);
        initializeCitizenWard(document);
        return document;
    }

    /**
     * Verifies a QR token AND the document's current status — the
     * original ID-card verify endpoint this replaces checked only the
     * HMAC signature, never whether the underlying document had since
     * been revoked, meaning a properly-signed but revoked card would
     * still have verified as VALID. Fixed here: a cryptographically
     * valid token for a REVOKED document now correctly reports invalid.
     */
    @Transactional(readOnly = true)
    public VerifyResult verify(String token) {
        QrCodeService.VerifyResult tokenResult = qrCodeService.verifyToken(token);
        if (!tokenResult.valid()) {
            return VerifyResult.invalid(tokenResult.reason());
        }

        UUID citizenId;
        DocumentType documentType;
        Instant issuedDate;
        try {
            citizenId = UUID.fromString(tokenResult.citizenId());
            documentType = DocumentType.valueOf(tokenResult.cardType());
            issuedDate = java.time.LocalDate.parse(tokenResult.issuedDate())
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return VerifyResult.invalid("MALFORMED_TOKEN_PAYLOAD");
        }

        List<OfficialDocument> matches = officialDocumentRepository
                .findByCitizenIdAndDocumentTypeAndIssuedAtBetweenOrderByIssuedAtDesc(
                        citizenId, documentType,
                        issuedDate, issuedDate.plus(1, ChronoUnit.DAYS));

        if (matches.isEmpty()) {
            return VerifyResult.invalid("DOCUMENT_NOT_FOUND");
        }

        OfficialDocument document = matches.get(0);
        if (document.getStatus() == DocumentStatus.REVOKED) {
            return VerifyResult.invalid("DOCUMENT_REVOKED");
        }
        if (document.getExpiresAt() != null && document.getExpiresAt().isBefore(Instant.now())) {
            return VerifyResult.invalid("DOCUMENT_EXPIRED");
        }

        initializeCitizenWard(document);
        return VerifyResult.valid(document);
    }

    // Forces Hibernate to resolve the citizen (and that citizen's own
    // ward) proxies while this service's transaction is still open — see
    // this class's Javadoc for why every public method here needs this
    // before returning.
    private void initializeCitizenWard(OfficialDocument document) {
        document.getCitizen().getWard().getWardNo();
    }

    private void issue(OfficialDocument document, UUID approverId) {
        Instant now = Instant.now();
        String issuedDateStr = now.atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();

        document.setStatus(DocumentStatus.ISSUED);
        document.setIssuedAt(now);
        document.setApprovedBy(approverId);
        document.setQrToken(qrCodeService.buildSignedToken(
                document.getCitizen().getId().toString(), document.getDocumentType().name(), issuedDateStr));

        if (!document.getDocumentType().isCertificate()) {
            // Instant has no calendar of its own — Instant.plus(Period)
            // throws UnsupportedTemporalTypeException at runtime (Period
            // is date-based, Instant is a bare point in time). Go via
            // ZonedDateTime to add calendar years correctly, then back.
            Instant expiresAt = now.atZone(java.time.ZoneOffset.UTC)
                    .plus(Period.ofYears(ID_CARD_VALIDITY_YEARS))
                    .toInstant();
            document.setExpiresAt(expiresAt);
        }
    }

    public record VerifyResult(boolean valid, OfficialDocument document, String reason) {
        static VerifyResult valid(OfficialDocument document) {
            return new VerifyResult(true, document, null);
        }
        static VerifyResult invalid(String reason) {
            return new VerifyResult(false, null, reason);
        }
    }
}
