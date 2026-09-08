package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformgrievance.dto.GrievanceResponse;
import np.gov.digital.platformgrievance.dto.GrievanceTransitionRequest;
import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.entity.GrievanceEvent;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.exception.InvalidGrievanceTransitionException;
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
public class GrievanceStateService {

    private final GrievanceRepository grievanceRepository;
    private final GrievanceEventRepository grievanceEventRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public GrievanceResponse transition(UUID grievanceId,
                                        GrievanceTransitionRequest request) {

        Grievance grievance = grievanceRepository.findById(grievanceId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Grievance not found: " + grievanceId));

        GrievanceStatus fromStatus = grievance.getStatus();
        GrievanceStatus toStatus   = request.getTargetStatus();

        // STEP 1 — Validate: throws InvalidGrievanceTransitionException (422) if bad
        GrievanceStateMachine.validate(fromStatus, toStatus);

        UUID actorId   = getActorId();
        String actorRole = getActorRole();
        Instant now    = Instant.now();

        // STEP 2 — Apply transition
        grievance.setStatus(toStatus);
        grievance.setUpdatedAt(now);

        // Set resolution fields depending on the target state
        if (toStatus == GrievanceStatus.RESOLVED_WARD) {
            grievance.setResolutionWard(request.getNote());
            grievance.setResolutionWardAt(now);
            grievance.setResolutionWardBy(actorId);
        }
        if (toStatus == GrievanceStatus.CLOSED_INVALID) {
            grievance.setRejectionReason(request.getNote());
            grievance.setClosedAt(now);
            grievance.setClosedBy(actorId);
        }

        Grievance saved = grievanceRepository.save(grievance);

        // STEP 3 — Append event log (mirrors citizen_events pattern)
        String eventType = resolveEventType(toStatus);
        grievanceEventRepository.save(GrievanceEvent.builder()
                .grievanceId(saved.getId())
                .citizenId(saved.getCitizenId())
                .eventType(eventType)
                .oldStatus(fromStatus.name())
                .newStatus(toStatus.name())
                .actorId(actorId)
                .actorRole(actorRole)
                .note(request.getNote())
                .createdAt(now)
                .build());

        // STEP 4 — Audit log
        auditLogService.log(AuditEventType.GRIEVANCE_RESOLVED, saved.getCitizenId(),
                "Grievance " + grievance.getTrackingCode() +
                        " transitioned " + fromStatus + " → " + toStatus);

        log.info("GrievanceStateService: {} transitioned {} → {} by actor={}",
                grievance.getTrackingCode(), fromStatus, toStatus, actorId);

        return GrievanceResponse.builder()
                .id(saved.getId())
                .citizenId(saved.getCitizenId())
                .category(saved.getCategory())
                .trackingCode(saved.getTrackingCode())
                .status(saved.getStatus())
                .filedAt(saved.getFiledAt())
                .slaDueAt(saved.getSlaDueAt())
                .message("Status updated to " + toStatus)
                .build();
    }

    /**
     * Returns the event type string for the event log.
     */
    private String resolveEventType(GrievanceStatus toStatus) {
        return switch (toStatus) {
            case IN_PROGRESS      -> "GRIEVANCE_IN_PROGRESS";
            case RESOLVED_WARD    -> "GRIEVANCE_RESOLVED_WARD";
            case CLOSED_INVALID   -> "GRIEVANCE_CLOSED_INVALID";
            case REFERRED_JUDICIAL-> "GRIEVANCE_REFERRED_JUDICIAL";
            case RESOLVED_JUDICIAL-> "GRIEVANCE_RESOLVED_JUDICIAL";
            case REFERRED_BOARD   -> "GRIEVANCE_REFERRED_BOARD";
            case RESOLVED_BOARD   -> "GRIEVANCE_RESOLVED_BOARD";
            case CLOSED           -> "GRIEVANCE_CLOSED";
            case REOPENED         -> "GRIEVANCE_REOPENED";
            default               -> "GRIEVANCE_STATUS_CHANGED";
        };
    }

    private UUID getActorId() {
        // BUG FIX: see AuthenticatedActor's javadoc (platform-audit) — was
        // parsing Authentication.getName() (the username/email) as a UUID.
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedActor actor) {
                return actor.getUserId();
            }
        } catch (Exception e) {
            log.warn("GrievanceStateService: could not extract actor UUID: {}", e.getMessage());
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
            log.warn("GrievanceStateService: could not extract actor role: {}", e.getMessage());
        }
        return "UNKNOWN";
    }
}