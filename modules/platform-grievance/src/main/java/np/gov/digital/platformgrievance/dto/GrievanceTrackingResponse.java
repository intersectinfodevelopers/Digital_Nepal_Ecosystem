package np.gov.digital.platformgrievance.dto;

import lombok.*;
import np.gov.digital.platformgrievance.enums.GrievanceCategory;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrievanceTrackingResponse {

    private String trackingCode;
    private GrievanceStatus status;
    private GrievanceCategory category;
    private Instant filedAt;
    private Instant slaDueAt;
    private boolean slaBreached;
    private String message;
}