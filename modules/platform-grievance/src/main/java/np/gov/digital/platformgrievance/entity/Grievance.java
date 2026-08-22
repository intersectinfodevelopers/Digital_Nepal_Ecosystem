package np.gov.digital.platformgrievance.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.platformgrievance.enums.GrievanceCategory;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "grievance",
        indexes = {
                @Index(name = "idx_grievance_citizen",      columnList = "citizen_id"),
                @Index(name = "idx_grievance_status",        columnList = "status"),
                @Index(name = "idx_grievance_tracking",      columnList = "tracking_code"),
                @Index(name = "idx_grievance_municipality",  columnList = "municipality_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Grievance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "citizen_id", nullable = false)
    private UUID citizenId;

    @Column(name = "filed_by")
    private UUID filedBy;

    @Column(name = "municipality_id")
    private UUID municipalityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private GrievanceCategory category;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attachment_urls", columnDefinition = "text[]")
    private List<String> attachmentUrls;

    @Column(name = "tracking_code", nullable = false, unique = true, length = 20)
    private String trackingCode;

    @Column(name = "filed_at", nullable = false)
    @Builder.Default
    private Instant filedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private GrievanceStatus status = GrievanceStatus.RECEIVED;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "escalated_by")
    private UUID escalatedBy;

    @Column(name = "resolution_ward", columnDefinition = "TEXT")
    private String resolutionWard;

    @Column(name = "resolution_ward_at")
    private Instant resolutionWardAt;

    @Column(name = "resolution_ward_by")
    private UUID resolutionWardBy;

    @Column(name = "resolution_judicial", columnDefinition = "TEXT")
    private String resolutionJudicial;

    @Column(name = "resolution_judicial_at")
    private Instant resolutionJudicialAt;

    @Column(name = "resolution_board", columnDefinition = "TEXT")
    private String resolutionBoard;

    @Column(name = "resolution_board_at")
    private Instant resolutionBoardAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "reopen_count", nullable = false)
    @Builder.Default
    private Short reopenCount = 0;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_breached", nullable = false)
    @Builder.Default
    private Boolean slaBreached = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}