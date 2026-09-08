package np.gov.digital.platformvitalevents.dto;

import lombok.*;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic response for any vital-event submission/approval/rejection —
 * shared across all five event types since the workflow fields
 * (id/type/status/ward/submitted/reviewed) live on the common vital_event
 * base table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VitalEventResponse {
    private UUID id;
    private VitalEventType eventType;
    private VitalEventStatus status;
    private UUID wardId;
    private Instant submittedAt;
    private Instant reviewedAt;
    private String rejectionReason;
    private String message;
}
