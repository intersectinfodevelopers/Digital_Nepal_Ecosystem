package np.gov.digital.platformevidenceexchange.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.platformevidenceexchange.enums.EvidenceAgency;
import np.gov.digital.platformevidenceexchange.enums.EvidencePurpose;
import np.gov.digital.platformevidenceexchange.enums.EvidenceRequestStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One logged, purpose-specific request per fact needed from an external
 * authoritative source (SDD Governance Tiers §7) — never a bulk export.
 * citizen EAGER, same reasoning as OfficialDocument/BenefitDisbursement:
 * every caller (the audit list, the response view) reads the citizen's
 * own fields outside this module's transaction boundary.
 */
@Entity
@Table(name = "evidence_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private Citizen citizen;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_agency", nullable = false, length = 30, updatable = false)
    private EvidenceAgency targetAgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50, updatable = false)
    private EvidencePurpose purpose;

    @Column(name = "fact_requested", nullable = false, length = 300, updatable = false)
    private String factRequested;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private EvidenceRequestStatus status = EvidenceRequestStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.requestedAt == null) this.requestedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
