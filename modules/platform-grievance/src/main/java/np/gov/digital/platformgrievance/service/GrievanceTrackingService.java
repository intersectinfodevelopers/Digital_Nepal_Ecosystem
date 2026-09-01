package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformgrievance.dto.GrievanceTrackingResponse;
import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrievanceTrackingService {

    private final GrievanceRepository grievanceRepository;

    public GrievanceTrackingResponse track(String trackingCode) {
        Grievance grievance = grievanceRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Grievance not found for tracking code: " + trackingCode));

        log.info("GrievanceTrackingService: public track lookup for code={}",
                trackingCode);

        // Build status message for citizen-friendly display
        String message = buildStatusMessage(grievance);

        // Return ONLY non-PII fields — no citizen_id, name, phone, description
        return GrievanceTrackingResponse.builder()
                .trackingCode(grievance.getTrackingCode())
                .status(grievance.getStatus())
                .category(grievance.getCategory())
                .filedAt(grievance.getFiledAt())
                .slaDueAt(grievance.getSlaDueAt())
                .slaBreached(grievance.getSlaBreached())
                .message(message)
                .build();
    }

    private String buildStatusMessage(Grievance g) {
        return switch (g.getStatus()) {
            case RECEIVED         -> "Your grievance has been received and is awaiting review.";
            case IN_PROGRESS      -> "Your grievance is currently being reviewed by the ward office.";
            case RESOLVED_WARD    -> "Your grievance has been resolved by the ward office.";
            case REFERRED_JUDICIAL-> "Your grievance has been referred to the Nyayik Samiti (Judicial Committee).";
            case RESOLVED_JUDICIAL-> "Your grievance has been resolved by the Nyayik Samiti.";
            case REFERRED_BOARD   -> "Your grievance has been referred to the Data Governance Board.";
            case RESOLVED_BOARD   -> "Your grievance has been resolved by the Data Governance Board.";
            case REFERRED_COMMISSION -> "Your grievance has been referred to the National Privacy Commission.";
            case CLOSED           -> "Your grievance has been closed.";
            case CLOSED_INVALID   -> "Your grievance has been reviewed and closed as invalid.";
            case REOPENED         -> "Your grievance has been reopened for further review.";
        };
    }
}