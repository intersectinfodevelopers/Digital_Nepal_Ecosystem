package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuditEventType;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformgrievance.dto.GrievanceFileRequest;
import np.gov.digital.platformgrievance.dto.GrievanceResponse;
import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.entity.GrievanceEvent;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.repository.GrievanceEventRepository;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import np.gov.digital.platformgrievance.util.TrackingCodeGenerator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrievanceService {

    private static final Duration SLA_WINDOW = Duration.ofHours(48);

    private final GrievanceRepository grievanceRepository;
    private final GrievanceEventRepository grievanceEventRepository;
    private final TrackingCodeGenerator trackingCodeGenerator;
    private final AuditLogService auditLogService;

    @Transactional
    public GrievanceResponse fileGrievance(GrievanceFileRequest request) {

        UUID filedBy        = getActorId();
        String actorRole    = getActorRole();
        UUID municipalityId = getMunicipalityId(); // captured for escalation check
        String trackingCode = trackingCodeGenerator.generate();
        Instant now         = Instant.now();

        Grievance grievance = Grievance.builder()
                .citizenId(request.getCitizenId())
                .filedBy(filedBy)
                .municipalityId(municipalityId)
                .category(request.getCategory())
                .description(request.getDescription())
                .attachmentUrls(request.getAttachmentUrls())
                .trackingCode(trackingCode)
                .filedAt(now)
                .status(GrievanceStatus.RECEIVED)
                .slaDueAt(now.plus(SLA_WINDOW))
                .slaBreached(false)
                .reopenCount((short) 0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Grievance saved = grievanceRepository.save(grievance);

        // Write first event log entry
        grievanceEventRepository.save(GrievanceEvent.builder()
                .grievanceId(saved.getId())
                .citizenId(saved.getCitizenId())
                .eventType("GRIEVANCE_SUBMITTED")
                .oldStatus(null)
                .newStatus(GrievanceStatus.RECEIVED.name())
                .actorId(filedBy)
                .actorRole(actorRole)
                .note("Grievance filed — category=" + request.getCategory())
                .createdAt(now)
                .build());

        auditLogService.log(AuditEventType.GRIEVANCE_SUBMITTED, saved.getCitizenId(),
                "trackingCode=" + trackingCode + " category=" + request.getCategory());

        log.info("GrievanceService: filed {} for citizen={} municipality={}",
                trackingCode, request.getCitizenId(), municipalityId);

        return GrievanceResponse.builder()
                .id(saved.getId())
                .citizenId(saved.getCitizenId())
                .category(saved.getCategory())
                .trackingCode(saved.getTrackingCode())
                .status(saved.getStatus())
                .filedAt(saved.getFiledAt())
                .slaDueAt(saved.getSlaDueAt())
                .message("Grievance filed successfully. Tracking code: " + trackingCode)
                .build();
    }

    private UUID getActorId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return UUID.fromString(auth.getName());
            }
        } catch (Exception e) {
            log.warn("GrievanceService: could not extract actor UUID: {}", e.getMessage());
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
            log.warn("GrievanceService: could not extract role: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    private UUID getMunicipalityId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof java.util.Map<?,?> details) {
                Object mId = details.get("municipality_id");
                if (mId != null) return UUID.fromString(mId.toString());
            }
        } catch (Exception e) {
            log.warn("GrievanceService: could not extract municipality_id from JWT: {}",
                    e.getMessage());
        }
        return null;
    }
}