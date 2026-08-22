package np.gov.digital.platformgrievance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformidcard.service.SparrowSmsService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrievanceNotificationService {

    private final SparrowSmsService sparrowSmsService;

    /**
     * Notifies the Ward Admin that their grievance has been escalated.
     * SMS failure is caught and logged — never propagated.
     *
     * @param mobileNumber Ward Admin's phone number (decrypted before calling)
     * @param trackingCode grievance tracking code e.g. GRV-2026-000123
     * @param newStatus    the new status e.g. REFERRED_JUDICIAL
     * @param reason       escalation reason
     */
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

            log.info("GrievanceNotificationService: Ward Admin notified of escalation " +
                    "trackingCode={} newStatus={}", trackingCode, newStatus);

        } catch (Exception e) {
            // SMS failure must never roll back escalation — log only
            log.error("GrievanceNotificationService: notification failed for " +
                    "trackingCode={} — {}", trackingCode, e.getMessage());
        }
    }

    /**
     * Notifies Ward Admin that their grievance was closed as invalid.
     */
    public void notifyWardAdminOfRejection(String mobileNumber,
                                           String trackingCode,
                                           String reason) {
        try {
            String message = "Grievance " + trackingCode +
                    " has been closed as invalid. Reason: " +
                    truncate(reason, 80) +
                    " - Kummayak Rural Municipality";

            sparrowSmsService.sendSms(mobileNumber, message);

            log.info("GrievanceNotificationService: Ward Admin notified of rejection " +
                    "trackingCode={}", trackingCode);

        } catch (Exception e) {
            log.error("GrievanceNotificationService: rejection notification failed for " +
                    "trackingCode={} — {}", trackingCode, e.getMessage());
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