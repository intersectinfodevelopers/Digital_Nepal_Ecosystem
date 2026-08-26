package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformidcard.service.SparrowSmsService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrievanceNotificationService {

    private final SparrowSmsService sparrowSmsService;

    public void notifyWardAdminOfEscalation(String mobileNumber,
                                            String trackingCode,
                                            String newStatus,
                                            String reason) {
        try {
            String message = "Grievance " + trackingCode +
                    " has been escalated to " + formatStatus(newStatus) +
                    ". Reason: " + truncate(reason, 80) +
                    " - Kummayak Rural Municipality";
            sparrowSmsService.sendSms(mobileNumber, message);
            log.info("Ward Admin notified of escalation trackingCode={}", trackingCode);
        } catch (Exception e) {
            log.error("Escalation SMS failed for {} — {}", trackingCode, e.getMessage());
        }
    }

    public void notifyWardAdminOfRejection(String mobileNumber,
                                           String trackingCode,
                                           String reason) {
        try {
            String message = "Grievance " + trackingCode +
                    " has been closed as invalid. Reason: " +
                    truncate(reason, 80) +
                    " - Kummayak Rural Municipality";
            sparrowSmsService.sendSms(mobileNumber, message);
            log.info("Ward Admin notified of rejection trackingCode={}", trackingCode);
        } catch (Exception e) {
            log.error("Rejection SMS failed for {} — {}", trackingCode, e.getMessage());
        }
    }
    public void notifyLocalBodyAdminOfSlaBreach(String trackingCode, UUID municipalityId) {
        try {
            // Structured in-app log — picked up by monitoring
            log.warn("SLA_BREACH_NOTIFICATION trackingCode={} municipality={}",
                    trackingCode, municipalityId);

            // sparrowSmsService.sendSms(localBodyPhone, message);
        } catch (Exception e) {
            log.error("SLA breach notification failed for {} — {}",
                    trackingCode, e.getMessage());
        }
    }


    public void notifyGrievanceFiled(String mobileNumber, String trackingCode) {
        try {
            sparrowSmsService.sendGrievanceTrackingNumber(mobileNumber, trackingCode);
            log.info("Grievance filed SMS sent trackingCode={}", trackingCode);
        } catch (Exception e) {
            log.error("Grievance filed SMS failed for {} — {}", trackingCode, e.getMessage());
        }
    }

    public void notifyGrievanceResolved(String mobileNumber, String trackingCode) {
        try {
            String message = "Your grievance " + trackingCode +
                    " has been resolved. " +
                    "Track status at your ward office or call Kummayak Municipality.";
            sparrowSmsService.sendSms(mobileNumber, message);
            log.info("Grievance resolved SMS sent trackingCode={}", trackingCode);
        } catch (Exception e) {
            log.error("Grievance resolved SMS failed for {} — {}", trackingCode, e.getMessage());
        }
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "REFERRED_JUDICIAL" -> "Nyayik Samiti (Judicial Committee)";
            case "REFERRED_BOARD"    -> "Data Governance Board";
            default                  -> status;
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}