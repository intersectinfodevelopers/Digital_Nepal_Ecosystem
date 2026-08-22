package np.gov.digital.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.auth.enums.ApprovalStatus;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "citizen_edit_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitizenEditRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID citizenId;

    @Column(nullable = false)
    private UUID wardId;

    @Column(nullable = false)
    private UUID submittedBy;

    private UUID approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(columnDefinition = "jsonb")
    private String oldValueJson;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String changePayload;

    private String rejectionReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private LocalDateTime escalatedAt;
}