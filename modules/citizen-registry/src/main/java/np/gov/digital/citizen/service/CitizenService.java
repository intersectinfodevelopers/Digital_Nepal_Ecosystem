package np.gov.digital.citizen.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.CitizenProfileResponse;
import np.gov.digital.citizen.dto.CitizenRegistrationRequest;
import np.gov.digital.citizen.dto.CitizenRegistrationResponse;
import np.gov.digital.citizen.dto.CitizenSummaryResponse;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.enums.CitizenStatus;
import np.gov.digital.citizen.enums.RelationType;
import np.gov.digital.citizen.enums.SyncStatus;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.DuplicateCitizenshipException;
import np.gov.digital.citizen.exception.DuplicateNidException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.repository.WardRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.citizen.vault.IdentityVaultService;
import np.gov.digital.citizen.vault.VaultType;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformgis.dto.GpsCaptureRequest;
import np.gov.digital.platformgis.service.CitizenGisService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CitizenService {

    private final CitizenRepository citizenRepository;
    private final WardRepository wardRepository;
    private final NidEncryptionUtil nidEncryptionUtil;
    private final AuditLogService auditLogService;
    private final FamilyLinkService familyLinkService;
    private final EligibilityService eligibilityService;
    private final CitizenGisService citizenGisService;
    private final IdentityVaultService identityVaultService;

    // REGISTRATION
    @Transactional
    public CitizenRegistrationResponse registerCitizen(CitizenRegistrationRequest request) {

        // STEP 1 — Validate ward
        Ward ward = wardRepository.findById(request.getWardId())
                .orElseThrow(() -> new WardNotFoundException(request.getWardId()));

        // STEP 2 — Compute pepper-based HMAC for duplicate check (SDD
        // Critical Implementation Note #2). We also still compute the
        // legacy plain-SHA-256 hash below purely so nid_hash stays
        // populated for existing NOT NULL constraint compatibility during
        // the V16 migration's transition period — it is not used for the
        // uniqueness decision anymore.
        String nidHmac = nidEncryptionUtil.hmac(request.getNid());
        // legacy column, not used for the uniqueness decision anymore:
        String nidHashLegacy = nidEncryptionUtil.hash(request.getNid());

        // Citizenship certificate and NID are legally independent documents
        // in Nepal (Extended Modules §2.2) — either must independently
        // dedupe, using the same HMAC+pepper construction as NID.
        String citizenshipHmac = nidEncryptionUtil.hmac(
                nidEncryptionUtil.normalizeCitizenshipNo(request.getCitizenshipNo()));

        // STEP 3 — Duplicate checks (HMAC-based, not the brute-forceable
        // plain hash) — NID and citizenship number each independently block.
        if (citizenRepository.existsByNidHmacAndIsActiveTrue(nidHmac)) {
            // Deliberately do not log the HMAC value itself — it is
            // derived from a secret pepper and, while not reversible to the
            // NID without the pepper, there is no operational reason to put
            // it in logs either.
            log.warn("Duplicate NID registration attempt for ward {}", request.getWardId());
            auditLogService.log(
                    AuditEventType.DUPLICATE_NID_ATTEMPT,
                    null,
                    "Duplicate NID attempt — ward: " + request.getWardId()
            );
            throw new DuplicateNidException("A citizen with this NID is already registered.");
        }

        if (citizenRepository.existsByCitizenshipHmacAndIsActiveTrue(citizenshipHmac)) {
            log.warn("Duplicate citizenship-number registration attempt for ward {}", request.getWardId());
            auditLogService.log(
                    AuditEventType.DUPLICATE_CITIZENSHIP_ATTEMPT,
                    null,
                    "Duplicate citizenship number attempt — ward: " + request.getWardId()
            );
            throw new DuplicateCitizenshipException("A citizen with this citizenship number is already registered.");
        }

        // STEP 4 — Encrypt PII fields. NID and citizenship number go into
        // the isolated identity vault (Extended Modules §2.3) — the
        // citizen row keeps only the reference token IdentityVaultService
        // hands back, never the ciphertext itself.
        UUID nidRef              = identityVaultService.store(VaultType.NID, request.getNid());
        UUID citizenshipRef      = identityVaultService.store(VaultType.CITIZENSHIP, request.getCitizenshipNo());
        String citizenshipNoNorm = nidEncryptionUtil.normalizeCitizenshipNo(request.getCitizenshipNo());
        String dobEnc            = nidEncryptionUtil.encrypt(request.getDob());
        String phoneEnc          = request.getPhone() != null
                ? nidEncryptionUtil.encrypt(request.getPhone()) : null;
        String phoneAltEnc       = request.getPhoneAlt() != null
                ? nidEncryptionUtil.encrypt(request.getPhoneAlt()) : null;
        String emailEnc          = request.getEmail() != null
                ? nidEncryptionUtil.encrypt(request.getEmail()) : null;
        String passportNoEnc     = request.getPassportNo() != null
                ? nidEncryptionUtil.encrypt(request.getPassportNo()) : null;

        // STEP 5 — Get actor from SecurityContext
        UUID actorId = getActorId();

        // STEP 6 — Build and save citizen
        Citizen citizen = Citizen.builder()
                .ward(ward)
                .nidRef(nidRef)
                .nidHash(nidHashLegacy)
                .nidHmac(nidHmac)
                .citizenshipRef(citizenshipRef)
                .citizenshipNoNorm(citizenshipNoNorm)
                .citizenshipHmac(citizenshipHmac)
                .passportNoEnc(passportNoEnc)
                .nameNp(request.getNameNp())
                .nameEn(request.getNameEn())
                .dobEnc(dobEnc)
                .sex(request.getSex())
                .bloodGroup(request.getBloodGroup())
                .religion(request.getReligion())
                .ethnicity(request.getEthnicity())
                .motherTongue(request.getMotherTongue())
                .tole(request.getTole())
                .phoneEnc(phoneEnc)
                .phoneAltEnc(phoneAltEnc)
                .emailEnc(emailEnc)
                .digitalLiteracy(request.getDigitalLiteracy())
                .hasSmartphone(request.getHasSmartphone() != null ? request.getHasSmartphone() : false)
                .photoUrl(request.getPhotoUrl())
                .consentRecordedAt(Instant.now())
                .consentChannel(request.getConsentChannel())
                .syncStatus(SyncStatus.SYNCED)
                .localRecordId(request.getLocalRecordId())
                .deviceId(request.getDeviceId())
                .registrationChannel(request.getRegistrationChannel())
                .nidVerified(false)
                .isAsyncVerified(false)
                .status(CitizenStatus.ACTIVE)
                .versionNumber(1)
                .createdBy(actorId)
                .build();

        // BUG FIX: was citizenRepository.save(citizen) — Citizen.id uses
        // Hibernate's in-memory GenerationType.UUID, so saved.getId() is
        // populated immediately, but the actual INSERT is deferred until
        // the next flush (normally transaction commit). AuditLogService
        // writes via a raw JdbcTemplate statement on the same connection/
        // transaction, executing immediately — so it always ran before the
        // citizen row physically existed in the DB, and citizen_events'
        // FK on citizen_id always failed. That failure was invisible until
        // now: before the AuthenticatedActor fix, AuditLogService's actor/
        // jurisdiction extraction always returned null and the method
        // returned before ever reaching the DB (see its class Javadoc) —
        // so citizen registration itself never surfaced this ordering bug.
        // saveAndFlush forces the INSERT to happen synchronously, so the
        // FK reference below is valid.
        Citizen saved = citizenRepository.saveAndFlush(citizen);

        // STEP 7 — Write audit log
        auditLogService.log(
                AuditEventType.CITIZEN_REGISTERED,
                saved.getId(),
                "Citizen registered in ward: " + ward.getId()
        );

        if (request.getGps() != null) {
            GpsCaptureRequest gpsCaptureRequest = GpsCaptureRequest.builder()
                    .latitude(request.getGps().getLatitude())
                    .longitude(request.getGps().getLongitude())
                    .accuracyM(request.getGps().getAccuracyM())
                    .elevationM(request.getGps().getElevationM())
                    .riskZone(request.getGps().getRiskZone())
                    .build();

            citizenGisService.captureAndStore(
                    saved.getId(),
                    ward.getId(),
                    actorId,
                    gpsCaptureRequest
            );
            log.info("GPS captured for citizen: {}", saved.getId());
        }

        // STEP 8 — Family tree linking
        // Build map of RelationType → normalized citizenship number from request
        Map<RelationType, String> familyMemberNos = buildFamilyMap(request);
        List<String> childrenCitizenshipNos = normalizeChildrenCitizenshipNos(request);
        if (!familyMemberNos.isEmpty() || !childrenCitizenshipNos.isEmpty()) {
            familyLinkService.createFamilyLinks(saved, familyMemberNos, childrenCitizenshipNos);
        }

        // STEP 9 — Resolve any PENDING links waiting for this citizen
        familyLinkService.resolvePendingLinks(saved);

        // STEP 10 — Run eligibility engine
        eligibilityService.evaluate(saved.getId());
        log.info("Eligibility evaluated for citizen: {}", saved.getId());

        log.info("Citizen registered — citizenId: {}, wardId: {}", saved.getId(), ward.getId());

        return CitizenRegistrationResponse.builder()
                .citizenId(saved.getId())
                .nameNp(saved.getNameNp())
                .nameEn(saved.getNameEn())
                .wardId(ward.getId())
                .nidVerified(saved.getNidVerified())
                .syncStatus(saved.getSyncStatus().name())
                .registeredAt(saved.getCreatedAt())
                .message("Citizen registered successfully. ID: " + saved.getId())
                .build();
    }

    // READ — single profile, decrypted for an authorized viewer.
    // Deliberately a separate DTO from CitizenRegistrationResponse, which is
    // scoped to "just confirm the registration succeeded," not a full view.
    @Transactional(readOnly = true)
    public CitizenProfileResponse getProfile(UUID citizenId) {
        Citizen citizen = citizenRepository.findById(citizenId)
                .filter(Citizen::getIsActive)
                .orElseThrow(() -> new CitizenNotFoundException(citizenId));

        return CitizenProfileResponse.builder()
                .citizenId(citizen.getId())
                .wardId(citizen.getWard().getId())
                .nameNp(citizen.getNameNp())
                .nameEn(citizen.getNameEn())
                .dob(nidEncryptionUtil.decrypt(citizen.getDobEnc()))
                .sex(citizen.getSex())
                .bloodGroup(citizen.getBloodGroup())
                .religion(citizen.getReligion())
                .ethnicity(citizen.getEthnicity())
                .motherTongue(citizen.getMotherTongue())
                .tole(citizen.getTole())
                .citizenshipNoMasked(maskLast4(citizen.getCitizenshipNoNorm()))
                .phone(citizen.getPhoneEnc() != null ? nidEncryptionUtil.decrypt(citizen.getPhoneEnc()) : null)
                .phoneAlt(citizen.getPhoneAltEnc() != null ? nidEncryptionUtil.decrypt(citizen.getPhoneAltEnc()) : null)
                .email(citizen.getEmailEnc() != null ? nidEncryptionUtil.decrypt(citizen.getEmailEnc()) : null)
                .digitalLiteracy(citizen.getDigitalLiteracy())
                .hasSmartphone(citizen.getHasSmartphone())
                .photoUrl(citizen.getPhotoUrl())
                .nidVerified(citizen.getNidVerified())
                .isActive(citizen.getIsActive())
                .syncStatus(citizen.getSyncStatus() != null ? citizen.getSyncStatus().name() : null)
                .registrationChannel(citizen.getRegistrationChannel())
                .registeredAt(citizen.getCreatedAt())
                .build();
    }

    // READ — paginated list, scoped to one ward. RLS (Postgres session
    // variables set per-request — see platform-audit's
    // RlsSessionVariableSetterTest) is the actual enforcement boundary for
    // "can this admin see this ward"; the wardId parameter here just picks
    // which page of an already-scoped result set to return.
    @Transactional(readOnly = true)
    public Page<CitizenSummaryResponse> listByWard(UUID wardId, Pageable pageable) {
        return citizenRepository.findByWardIdAndIsActiveTrue(wardId, pageable)
                .map(this::toSummary);
    }

    // DELETE — soft deactivate. Never a hard delete: a citizen record is a
    // legal artifact, not disposable state. isActive is now a database-
    // generated column (see V27) driven by status, which is what this
    // actually writes.
    //
    // Restricted to VOIDED_DUPLICATE/VOIDED_FRAUD: DECEASED and
    // RENOUNCED_CITIZENSHIP are outcomes of their own dedicated workflows
    // (the death-record cascade and a future renunciation flow — see
    // Extended Modules §4.3), not a generic admin action. This endpoint is
    // specifically for "this record shouldn't have existed / was voided,"
    // not "this person's legal status changed."
    @Transactional
    public void deactivate(UUID citizenId, CitizenStatus voidStatus, String reason) {
        if (voidStatus != CitizenStatus.VOIDED_DUPLICATE && voidStatus != CitizenStatus.VOIDED_FRAUD) {
            throw new IllegalArgumentException(
                    "Deactivation must be VOIDED_DUPLICATE or VOIDED_FRAUD — "
                            + "DECEASED and RENOUNCED_CITIZENSHIP go through their own vital-event workflows, not this endpoint.");
        }

        Citizen citizen = citizenRepository.findById(citizenId)
                .filter(Citizen::getIsActive)
                .orElseThrow(() -> new CitizenNotFoundException(citizenId));

        citizen.setStatus(voidStatus);
        citizen.setArchivedAt(Instant.now());
        citizen.setArchivedBy(getActorId());
        citizenRepository.save(citizen);

        auditLogService.log(
                AuditEventType.CITIZEN_ARCHIVED,
                citizenId,
                reason != null && !reason.isBlank()
                        ? "Citizen deactivated (" + voidStatus + "): " + reason
                        : "Citizen deactivated (" + voidStatus + ")"
        );

        log.info("Citizen deactivated — citizenId: {}, status: {}", citizenId, voidStatus);
    }

    // PRIVATE HELPERS

    private CitizenSummaryResponse toSummary(Citizen citizen) {
        return CitizenSummaryResponse.builder()
                .citizenId(citizen.getId())
                .nameNp(citizen.getNameNp())
                .nameEn(citizen.getNameEn())
                .wardId(citizen.getWard().getId())
                .sex(citizen.getSex())
                .nidVerified(citizen.getNidVerified())
                .isActive(citizen.getIsActive())
                .syncStatus(citizen.getSyncStatus() != null ? citizen.getSyncStatus().name() : null)
                .registeredAt(citizen.getCreatedAt())
                .build();
    }

    private String maskLast4(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 4) return value;
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    /**
     * Builds the father/mother/spouse map from the registration request.
     * Citizenship numbers are normalized the same way as the citizen's own
     * (see NidEncryptionUtil.normalizeCitizenshipNo) so lookups against
     * citizenshipNoNorm match regardless of formatting differences between
     * what the two family members each typed in.
     */
    private Map<RelationType, String> buildFamilyMap(CitizenRegistrationRequest request) {
        Map<RelationType, String> map = new HashMap<>();
        putIfPresent(map, RelationType.FATHER, request.getFatherCitizenshipNo());
        putIfPresent(map, RelationType.MOTHER, request.getMotherCitizenshipNo());
        putIfPresent(map, RelationType.SPOUSE, request.getSpouseCitizenshipNo());
        return map;
    }

    private void putIfPresent(Map<RelationType, String> map, RelationType type, String rawCitizenshipNo) {
        if (rawCitizenshipNo != null && !rawCitizenshipNo.isBlank()) {
            map.put(type, nidEncryptionUtil.normalizeCitizenshipNo(rawCitizenshipNo));
        }
    }

    private List<String> normalizeChildrenCitizenshipNos(CitizenRegistrationRequest request) {
        if (request.getChildrenCitizenshipNos() == null) return List.of();
        return request.getChildrenCitizenshipNos().stream()
                .filter(no -> no != null && !no.isBlank())
                .map(nidEncryptionUtil::normalizeCitizenshipNo)
                .toList();
    }

    private UUID getActorId() {
        // BUG FIX: this used to call UUID.fromString(auth.getName()) —
        // Authentication.getName() for a UserDetails principal returns the
        // *username* (email, in this codebase), not a UUID, so parsing it
        // as one always threw and silently fell back to the placeholder
        // below on every real authenticated request. AuthenticatedActor
        // (platform-audit) lets this module read the real actor's UUID off
        // the principal without a circular dependency on the auth module.
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
