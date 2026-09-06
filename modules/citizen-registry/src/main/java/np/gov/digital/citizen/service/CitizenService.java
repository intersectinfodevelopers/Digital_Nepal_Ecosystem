package np.gov.digital.citizen.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.CitizenProfileResponse;
import np.gov.digital.citizen.dto.CitizenRegistrationRequest;
import np.gov.digital.citizen.dto.CitizenRegistrationResponse;
import np.gov.digital.citizen.dto.CitizenSummaryResponse;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.enums.RelationType;
import np.gov.digital.citizen.enums.SyncStatus;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.exception.DuplicateNidException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.repository.WardRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
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

        // STEP 3 — Duplicate check (HMAC-based, not the brute-forceable
        // plain hash)
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

        // STEP 4 — Encrypt PII fields
        String nidEnc            = nidEncryptionUtil.encrypt(request.getNid());
        String citizenshipNoEnc  = nidEncryptionUtil.encrypt(request.getCitizenshipNo());
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
                .nidEnc(nidEnc)
                .nidHash(nidHashLegacy)
                .nidHmac(nidHmac)
                .citizenshipNoEnc(citizenshipNoEnc)
                .citizenshipNoNorm(citizenshipNoNorm)
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
                .isActive(true)
                .versionNumber(1)
                .createdBy(actorId)
                .build();

        Citizen saved = citizenRepository.save(citizen);

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
    // legal artifact, not disposable state. Every existing read query
    // already filters on isActive; this is the one place that writes it.
    @Transactional
    public void deactivate(UUID citizenId, String reason) {
        Citizen citizen = citizenRepository.findById(citizenId)
                .filter(Citizen::getIsActive)
                .orElseThrow(() -> new CitizenNotFoundException(citizenId));

        citizen.setIsActive(false);
        citizenRepository.save(citizen);

        auditLogService.log(
                AuditEventType.CITIZEN_ARCHIVED,
                citizenId,
                reason != null && !reason.isBlank()
                        ? "Citizen deactivated: " + reason
                        : "Citizen deactivated"
        );

        log.info("Citizen deactivated — citizenId: {}", citizenId);
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
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return UUID.fromString(auth.getName());
            }
        } catch (Exception e) {
            log.warn("Could not extract actor ID from SecurityContext — using placeholder");
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
