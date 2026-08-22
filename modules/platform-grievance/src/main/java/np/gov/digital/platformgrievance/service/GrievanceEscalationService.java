package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgrievance.dto.GrievanceEscalationRequest;
import np.gov.digital.platformgrievance.dto.GrievanceRejectionRequest;
import np.gov.digital.platformgrievance.dto.GrievanceResponse;
import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.entity.GrievanceEvent;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.exception.GrievanceEscalationException;
import np.gov.digital.platformgrievance.repository.GrievanceEventRepository;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import np.gov.digital.platformgrievance.statemachine.GrievanceStateMachine;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrievanceEscalationService {

    private final GrievanceRepository grievanceRepository;
    private final GrievanceEventRepository grievanceEventRepository;
    private final GrievanceNotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public GrievanceResponse escalateToJudicial(UUID grievanceId,
                                                GrievanceEscalationRequest request,
                                                String wardAdminMobile) {

        Grievance grievance = findGrievance(grievanceId);

        // SAME-MUNICIPALITY CHECK — SDD 5.3 + Day 12 integration checklist
        if (grievance.getMunicipalityId() != null &&
                !grievance.getMunicipalityId().equals(request.getMunicipalityId())) {
            log.warn("GrievanceEscalationService: cross-municipality escalation " +
                            "attempt blocked — grievance={} requestMunicipality={}",
                    grievanceId, request.getMunicipalityId());
            throw new GrievanceEscalationException(
                    "Escalation blocked: grievance belongs to a different municipality. " +
                            "Cross-municipality escalation is not permitted (SDD 5.3).");
        }

        // Validate state transition via state machine
        GrievanceStateMachine.validate(grievance.getStatus(), GrievanceStatus.REFERRED_JUDICIAL);

        UUID actorId     = getActorId();
        String actorRole = getActorRole();
        Instant now      = Instant.now();

        GrievanceStatus fromStatus = grievance.getStatus();
        grievance.setStatus(GrievanceStatus.REFERRED_JUDICIAL);
        grievance.setEscalatedAt(now);
        grievance.setEscalatedBy(actorId);
        grievance.setUpdatedAt(now);

        Grievance saved = grievanceRepository.save(grievance);

        // Append event log
        grievanceEventRepository.save(GrievanceEvent.builder()
                .grievanceId(saved.getId())
                .citizenId(saved.getCitizenId())
                .eventType("GRIEVANCE_REFERRED_JUDICIAL")
                .oldStatus(fromStatus.name())
                .newStatus(GrievanceStatus.REFERRED_JUDICIAL.name())
                .actorId(actorId)
                .actorRole(actorRole)
                .note(request.getReason())
                .createdAt(now)
                .build());

        auditLogService.log(AuditEventType.GRIEVANCE_RESOLVED, saved.getCitizenId(),
                "Grievance " + saved.getTrackingCode() +
                        " escalated to Nyayik Samiti — reason: " + request.getReason());

        // SMS notification — ALWAYS in try-catch so failure never rolls back escalation
        try {
            if (wardAdminMobile != null && !wardAdminMobile.isBlank()) {
                notificationService.notifyWardAdminOfEscalation(
                        wardAdminMobile,
                        saved.getTrackingCode(),
                        GrievanceStatus.REFERRED_JUDICIAL.name(),
                        request.getReason()
                );
            }
        } catch (Exception e) {
            // SMS failure must NEVER roll back a committed escalation
            log.error("GrievanceEscalationService: Ward Admin SMS failed for {} — {}",
                    saved.getTrackingCode(), e.getMessage());
        }

        log.info("GrievanceEscalationService: {} escalated to REFERRED_JUDICIAL by actor={}",
                saved.getTrackingCode(), actorId);

        return buildResponse(saved, "Grievance escalated to Nyayik Samiti successfully.");
    }

    @Transactional
    public GrievanceResponse closeInvalid(UUID grievanceId,
                                          GrievanceRejectionRequest request,
                                          String wardAdminMobile) {

        Grievance grievance = findGrievance(grievanceId);

        GrievanceStateMachine.validate(grievance.getStatus(), GrievanceStatus.CLOSED_INVALID);

        UUID actorId     = getActorId();
        String actorRole = getActorRole();
        Instant now      = Instant.now();

        GrievanceStatus fromStatus = grievance.getStatus();
        grievance.setStatus(GrievanceStatus.CLOSED_INVALID);
        grievance.setRejectionReason(request.getRejectionReason());
        grievance.setClosedAt(now);
        grievance.setClosedBy(actorId);
        grievance.setUpdatedAt(now);

        Grievance saved = grievanceRepository.save(grievance);

        grievanceEventRepository.save(GrievanceEvent.builder()
                .grievanceId(saved.getId())
                .citizenId(saved.getCitizenId())
                .eventType("GRIEVANCE_CLOSED_INVALID")
                .oldStatus(fromStatus.name())
                .newStatus(GrievanceStatus.CLOSED_INVALID.name())
                .actorId(actorId)
                .actorRole(actorRole)
                .note(request.getRejectionReason())
                .createdAt(now)
                .build());

        auditLogService.log(AuditEventType.GRIEVANCE_RESOLVED, saved.getCitizenId(),
                "Grievance " + saved.getTrackingCode() +
                        " closed as invalid — reason: " + request.getRejectionReason());

        // SMS notification — ALWAYS in try-catch
        try {
            if (wardAdminMobile != null && !wardAdminMobile.isBlank()) {
                notificationService.notifyWardAdminOfRejection(
                        wardAdminMobile,
                        saved.getTrackingCode(),
                        request.getRejectionReason()
                );
            }
        } catch (Exception e) {
            log.error("GrievanceEscalationService: rejection SMS failed for {} — {}",
                    saved.getTrackingCode(), e.getMessage());
        }

        log.info("GrievanceEscalationService: {} closed as CLOSED_INVALID by actor={}",
                saved.getTrackingCode(), actorId);

        return buildResponse(saved, "Grievance closed as invalid.");
    }

    private Grievance findGrievance(UUID grievanceId) {
        return grievanceRepository.findById(grievanceId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Grievance not found: " + grievanceId));
    }

    private GrievanceResponse buildResponse(Grievance saved, String message) {
        return GrievanceResponse.builder()
                .id(saved.getId())
                .citizenId(saved.getCitizenId())
                .category(saved.getCategory())
                .trackingCode(saved.getTrackingCode())
                .status(saved.getStatus())
                .filedAt(saved.getFiledAt())
                .slaDueAt(saved.getSlaDueAt())
                .message(message)
                .build();
    }

    private UUID getActorId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return UUID.fromString(auth.getName());
            }
        } catch (Exception e) {
            log.warn("GrievanceEscalationService: could not extract actor UUID: {}",
                    e.getMessage());
        }
        return null;
    }

    private String getActorRole() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && !auth.getAuthorities().isEmpty()) {
                return auth.getAuthorities().iterator().next().getAuthority();
            }
        } catch (Exception e) {
            log.warn("GrievanceEscalationService: could not extract role: {}",
                    e.getMessage());
        }
        return "UNKNOWN";
    }
}