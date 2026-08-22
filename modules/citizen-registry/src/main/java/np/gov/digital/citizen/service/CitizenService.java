package np.gov.digital.citizen.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.dto.CitizenRegistrationRequest;
import np.gov.digital.citizen.dto.CitizenRegistrationResponse;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.enums.RelationType;
import np.gov.digital.citizen.enums.SyncStatus;
import np.gov.digital.citizen.exception.DuplicateNidException;
import np.gov.digital.citizen.exception.WardNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.repository.WardRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgis.dto.GpsCaptureRequest;
import np.gov.digital.platformgis.service.CitizenGisService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
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
        if (!familyMemberNos.isEmpty()) {
            familyLinkService.createFamilyLinks(saved, familyMemberNos);
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

    // PRIVATE HELPERS

    /**
     * Builds family member map from registration request.
     * Add more relation types here as the request DTO grows.
     */
    private Map<RelationType, String> buildFamilyMap(CitizenRegistrationRequest request) {
        Map<RelationType, String> map = new HashMap<>();
        return map;
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
