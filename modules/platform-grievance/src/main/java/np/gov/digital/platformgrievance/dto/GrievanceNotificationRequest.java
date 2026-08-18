package np.gov.digital.platformgrievance.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrievanceNotificationRequest {
    private String mobileNumber;
    private String trackingCode;
    private String newStatus;
    private String note;
}