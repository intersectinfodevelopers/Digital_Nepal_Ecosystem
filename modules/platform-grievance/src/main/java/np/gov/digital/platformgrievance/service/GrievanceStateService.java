package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
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
    private final GrievanceNotificationService notificationService;

    @Transactional
    public GrievanceResponse transition(UUID grievanceId,
                                        GrievanceTransitionRequest request) {

        Grievance grievance = grievanceRepository.findById(grievanceId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Grievance not found: " + grievanceId));

        GrievanceStatus fromStatus = grievance.getStatus();
        GrievanceStatus toStatus   = request.getTargetStatus();

        // Validate via state machine — throws 422 on bad transition
        GrievanceStateMachine.validate(fromStatus, toStatus);

        UUID actorId     = getActorId();
        String actorRole = getActorRole();
        Instant now      = Instant.now();

        // Apply transition
        grievance.setStatus(toStatus);
        grievance.setUpdatedAt(now);

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

        // Append event log
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

        auditLogService.log(AuditEventType.GRIEVANCE_RESOLVED, saved.getCitizenId(),
                "Grievance " + grievance.getTrackingCode() +
                        " transitioned " + fromStatus + " → " + toStatus);

        if (request.getCitizenMobile() != null && isResolvedState(toStatus)) {
            try {
                notificationService.notifyGrievanceResolved(
                        request.getCitizenMobile(),
                        saved.getTrackingCode());
            } catch (Exception e) {
                log.error("GrievanceStateService: resolved SMS failed for {} — {}",
                        saved.getTrackingCode(), e.getMessage());
            }
        }

        log.info("GrievanceStateService: {} transitioned {} → {}",
                grievance.getTrackingCode(), fromStatus, toStatus);

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

    private boolean isResolvedState(GrievanceStatus status) {
        return switch (status) {
            case RESOLVED_WARD, RESOLVED_JUDICIAL,
                 RESOLVED_BOARD, CLOSED -> true;
            default -> false;
        };
    }

    private String resolveEventType(GrievanceStatus toStatus) {
        return switch (toStatus) {
            case IN_PROGRESS       -> "GRIEVANCE_IN_PROGRESS";
            case RESOLVED_WARD     -> "GRIEVANCE_RESOLVED_WARD";
            case CLOSED_INVALID    -> "GRIEVANCE_CLOSED_INVALID";
            case REFERRED_JUDICIAL -> "GRIEVANCE_REFERRED_JUDICIAL";
            case RESOLVED_JUDICIAL -> "GRIEVANCE_RESOLVED_JUDICIAL";
            case REFERRED_BOARD    -> "GRIEVANCE_REFERRED_BOARD";
            case RESOLVED_BOARD    -> "GRIEVANCE_RESOLVED_BOARD";
            case CLOSED            -> "GRIEVANCE_CLOSED";
            case REOPENED          -> "GRIEVANCE_REOPENED";
            default                -> "GRIEVANCE_STATUS_CHANGED";
        };
    }

    private UUID getActorId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null)
                return UUID.fromString(auth.getName());
        } catch (Exception e) {
            log.warn("GrievanceStateService: could not extract actor UUID: {}", e.getMessage());
        }
        return null;
    }

    private String getActorRole() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && !auth.getAuthorities().isEmpty())
                return auth.getAuthorities().iterator().next().getAuthority();
        } catch (Exception e) {
            log.warn("GrievanceStateService: could not extract role: {}", e.getMessage());
        }
        return "UNKNOWN";
    }
}