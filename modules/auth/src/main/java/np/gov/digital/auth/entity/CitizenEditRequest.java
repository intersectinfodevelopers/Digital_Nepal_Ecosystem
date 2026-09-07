package np.gov.digital.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.auth.enums.ApprovalStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    // BUG FIX: columnDefinition = "jsonb" alone doesn't tell Hibernate how
    // to bind the parameter — a plain String still goes over the wire as
    // VARCHAR, which Postgres rejects on every INSERT/UPDATE ("column is
    // of type jsonb but expression is of type character varying"). Found
    // live while building an unrelated JSONB column that copied this same
    // pattern; see platform-vital-events' VerbalAutopsyResponse.responses
    // for where it was first reproduced, and platform-sync's
    // SyncRecord.payload for the one place in this codebase that already
    // had @JdbcTypeCode(SqlTypes.JSON) and didn't have the bug. This means
    // submitting a citizen edit request has never worked against a real
    // database.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String oldValueJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String changePayload;

    private String rejectionReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private LocalDateTime escalatedAt;
}