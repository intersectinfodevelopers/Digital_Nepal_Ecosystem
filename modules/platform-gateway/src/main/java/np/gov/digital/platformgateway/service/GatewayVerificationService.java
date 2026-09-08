package np.gov.digital.platformgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.exception.CitizenNotFoundException;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.util.NidEncryptionUtil;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgateway.dto.VerifyRequest;
import np.gov.digital.platformgateway.dto.VerifyResponse;
import np.gov.digital.platformgateway.entity.*;
import np.gov.digital.platformgateway.enums.AccessOutcome;
import np.gov.digital.platformgateway.enums.ConsentMethod;
import np.gov.digital.platformgateway.enums.ConsentStatus;
import np.gov.digital.platformgateway.enums.PurposeStatus;
import np.gov.digital.platformgateway.enums.RelyingPartyStatus;
import np.gov.digital.platformgateway.exception.*;
import np.gov.digital.platformgateway.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The actual "verify one fact" call (Extended Modules §6.3-6.5) —
 * everything a relying party does with the gateway funnels through
 * here: client authentication, scope check, consent check, the pairwise
 * token, and the real-time anomaly response.
 *
 * Client authentication is a checked clientId/clientSecret pair, not the
 * OAuth2 client-credentials token-issuance-and-bearer-validation flow
 * the design doc names — a deliberate simplification, documented here
 * rather than silently substituted: building a full second
 * authentication filter chain (distinct from the JWT filter that
 * authenticates citizens/admins) to issue and validate gateway-specific
 * bearer tokens is real, separate infrastructure, and the credential
 * check itself — is this relying party who it claims to be, checked
 * against a bcrypt hash, revocable, suspendable — is the actual security
 * property that matters here. Same category of scoping decision as the
 * mTLS stand-in in V41's migration comment.
 *
 * Anomaly response (§6.5) is real-time, not a periodic scan:
 * consecutiveDeniedCount increments on every DENIED outcome and resets
 * on SUCCESS, checked inline the moment a denial happens rather than
 * waiting for GatewayAnomalyMonitorJob (not built) to notice later. The
 * automatic ladder stops at "suspend the party" — automatically
 * revoking a relying party from a counter alone, with no human review,
 * isn't a call this service makes on its own; revoke() is a deliberate
 * Central Admin action.
 *
 * Every denial path below ends by throwing — deliberately, that's how a
 * relying party finds out verify() failed. Because this whole method is
 * @Transactional, that throw would roll back the entire transaction,
 * including the anomaly counter and access-log writes the denial itself
 * is supposed to produce (found live — see GatewayDenialRecorder's class
 * Javadoc for the full story). So the actual writes on any denial path
 * run through GatewayDenialRecorder's own REQUIRES_NEW transaction
 * first, and only then does this method throw.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayVerificationService {

    private final RelyingPartyRepository relyingPartyRepository;
    private final RelyingPartyPurposeRepository relyingPartyPurposeRepository;
    private final CitizenRelyingPartyTokenRepository tokenRepository;
    private final GatewayConsentRepository gatewayConsentRepository;
    private final GatewayAccessLogRepository gatewayAccessLogRepository;
    private final CitizenRepository citizenRepository;
    private final NidEncryptionUtil nidEncryptionUtil;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final RelyingPartyAuthenticator relyingPartyAuthenticator;
    private final GatewayDenialRecorder gatewayDenialRecorder;

    @Transactional
    public VerifyResponse verify(String clientId, String clientSecret, String purposeCode, VerifyRequest request) {
        RelyingParty relyingParty = relyingPartyAuthenticator.authenticate(clientId, clientSecret);

        Citizen citizen = citizenRepository.findById(request.getCitizenId())
                .orElseThrow(() -> new CitizenNotFoundException(request.getCitizenId()));

        if (relyingParty.getStatus() != RelyingPartyStatus.ACTIVE
                || (relyingParty.getCertificateExpiresAt() != null
                        && relyingParty.getCertificateExpiresAt().isBefore(Instant.now()))) {
            return deny(citizen, relyingParty, purposeCode, AccessOutcome.DENIED_SUSPENDED, null,
                    "Relying party is not active (suspended, revoked, or certificate lapsed).");
        }

        RelyingPartyPurpose purpose = relyingPartyPurposeRepository
                .findByRelyingParty_IdAndPurposeCode(relyingParty.getId(), purposeCode)
                .orElse(null);
        if (purpose == null || purpose.getStatus() == PurposeStatus.SUSPENDED) {
            return deny(citizen, relyingParty, purposeCode, AccessOutcome.DENIED_SCOPE, null,
                    "Relying party is not licensed for purpose " + purposeCode
                            + " (ERR_RELYING_PARTY_SCOPE_DENIED).");
        }

        ConsentMethod consentMethod;
        if (Boolean.FALSE.equals(purpose.getRequiresConsent())) {
            consentMethod = ConsentMethod.LEGAL_MANDATE_NO_CONSENT;
        } else {
            boolean hasConfirmedConsent = gatewayConsentRepository
                    .findTopByCitizen_IdAndRelyingPartyIdAndPurposeCodeAndStatusOrderByCreatedAtDesc(
                            citizen.getId(), relyingParty.getId(), purposeCode, ConsentStatus.CONFIRMED)
                    .isPresent();
            if (!hasConfirmedConsent) {
                return deny(citizen, relyingParty, purposeCode, AccessOutcome.DENIED_CONSENT, null,
                        "Citizen has not confirmed OTP consent for this purpose "
                                + "(ERR_RELYING_PARTY_CONSENT_REQUIRED).");
            }
            consentMethod = ConsentMethod.OTP;
        }

        // Success — reset the anomaly counter and return the whitelisted
        // fields via this relying party's own pairwise token.
        relyingParty.setConsecutiveDeniedCount(0);
        relyingPartyRepository.save(relyingParty);

        UUID pairwiseToken = getOrCreatePairwiseToken(citizen, relyingParty.getId());
        Map<String, Object> fields = extractFields(citizen, purpose.getAllowedFields());

        logAccess(citizen, relyingParty, purposeCode, fields, consentMethod, AccessOutcome.SUCCESS);
        auditLogService.log(AuditEventType.GATEWAY_VERIFY_SUCCESS, citizen.getId(),
                relyingParty.getName() + " verified " + purposeCode + " for this citizen");

        return VerifyResponse.builder()
                .pairwiseToken(pairwiseToken)
                .purposeCode(purposeCode)
                .fields(fields)
                .build();
    }

    private VerifyResponse deny(Citizen citizen, RelyingParty relyingParty, String purposeCode,
                                 AccessOutcome outcome, ConsentMethod consentMethod, String message) {
        // Committed in its own transaction BEFORE the throw below unwinds
        // this one — see class Javadoc and GatewayDenialRecorder.
        gatewayDenialRecorder.recordDenial(citizen.getId(), relyingParty.getId(), purposeCode,
                outcome, consentMethod, message);

        throw switch (outcome) {
            case DENIED_SCOPE -> new ScopeDeniedException(message);
            case DENIED_CONSENT -> new ConsentRequiredException(message);
            case DENIED_SUSPENDED -> new RelyingPartySuspendedException(message);
            case SUCCESS -> new IllegalStateException("Unreachable — SUCCESS is not a denial outcome.");
        };
    }

    private UUID getOrCreatePairwiseToken(Citizen citizen, UUID relyingPartyId) {
        return tokenRepository.findByCitizen_IdAndRelyingPartyId(citizen.getId(), relyingPartyId)
                .map(CitizenRelyingPartyToken::getToken)
                .orElseGet(() -> tokenRepository.save(CitizenRelyingPartyToken.builder()
                        .citizen(citizen)
                        .relyingPartyId(relyingPartyId)
                        .build()).getToken());
    }

    /**
     * Curated, safe field set — deliberately NOT a fully dynamic/
     * reflection-based extractor over Citizen's own fields, which could
     * accidentally expose an internal/encrypted column if allowedFields
     * were ever misconfigured. Field names deliberately match
     * CitizenProfileResponse's own vocabulary exactly (wardId,
     * citizenshipNoMasked, etc.) so an admin configuring
     * AddPurposeRequest.allowedFields already knows the names to use.
     */
    private Map<String, Object> extractFields(Citizen citizen, String allowedFieldsJson) {
        List<String> allowedFields = parseAllowedFields(allowedFieldsJson);
        Map<String, Object> result = new HashMap<>();

        for (String field : allowedFields) {
            switch (field) {
                case "nameEn" -> result.put(field, citizen.getNameEn());
                case "nameNp" -> result.put(field, citizen.getNameNp());
                case "sex" -> result.put(field, citizen.getSex());
                case "dob" -> result.put(field, nidEncryptionUtil.decrypt(citizen.getDobEnc()));
                case "wardId" -> result.put(field, citizen.getWard().getId());
                case "status" -> result.put(field, citizen.getStatus().name());
                case "citizenshipNoMasked" -> result.put(field, maskLast4(citizen.getCitizenshipNoNorm()));
                case "maritalStatus" -> result.put(field, citizen.getMaritalStatus() != null
                        ? citizen.getMaritalStatus().name() : null);
                default -> log.warn("GatewayVerificationService: allowedFields lists unrecognized field '{}' — skipped, not exposed", field);
            }
        }
        return result;
    }

    private String maskLast4(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 4) return value;
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseAllowedFields(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse allowedFields", e);
        }
    }

    private void logAccess(Citizen citizen, RelyingParty relyingParty, String purposeCode,
                            Map<String, Object> fields, ConsentMethod consentMethod, AccessOutcome outcome) {
        String fieldsJson = fields != null ? writeJson(fields.keySet()) : null;
        GatewayAccessLog accessLog = GatewayAccessLog.builder()
                .citizen(citizen)
                .relyingPartyId(relyingParty.getId())
                .purposeCode(purposeCode)
                .fieldsDisclosed(fieldsJson)
                .consentMethod(consentMethod)
                .outcome(outcome)
                .build();
        gatewayAccessLogRepository.save(accessLog);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
