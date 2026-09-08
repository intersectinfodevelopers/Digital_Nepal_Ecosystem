package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.platformvitalevents.enums.VitalEventStatus;
import np.gov.digital.platformvitalevents.enums.VitalEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Base table shared by all five vital event types (SDD Extended Modules
 * §4.1). Each concrete event type (BirthRecord, and — as later increments
 * land — DeathRecord, MarriageRecord, DivorceRecord, MigrationRecord) is a
 * separate 1:1 detail table joined on vital_event_id, so the workflow
 * (submission, approval, escalation) lives in exactly one place instead of
 * being duplicated five times.
 *
 * DESIGN NOTE — mutable, not append-only: the design doc's phrasing for
 * this table ("UPDATE/DELETE revoked from the application role entirely")
 * is citizen_events' own append-only discipline, and doesn't actually fit
 * a row that has to move through a state machine (SUBMITTED →
 * PENDING_APPROVAL → APPROVED/REJECTED/CAO_REVIEW) via the same case
 * record — an append-only table would need every transition to be a new
 * row keyed by a stable case id, which the design doesn't otherwise
 * define. Rather than force that redesign in silently, vital_event is
 * built as a normal mutable workflow row — the same pattern this codebase
 * already uses for citizen_edit_requests/ApprovalService. Tamper-evident,
 * hash-chained history for every transition instead comes from
 * citizen_events (already hash-chained per V31): VitalEventService logs
 * VITAL_EVENT_SUBMITTED / _APPROVED / _REJECTED / _ESCALATED there on every
 * transition, so the full history is still permanently, verifiably
 * recorded — just in the existing audit trail rather than by making this
 * table itself immutable. Direct hash-chaining on vital_event's own rows
 * is tracked as a Security & Tamper-Evidence follow-up, not dropped
 * silently.
 */
@Entity
@Table(name = "vital_event", indexes = {
        @Index(name = "idx_vital_event_ward", columnList = "ward_id"),
        @Index(name = "idx_vital_event_status", columnList = "status"),
        @Index(name = "idx_vital_event_type", columnList = "event_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VitalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20, updatable = false)
    private VitalEventType eventType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private VitalEventStatus status = VitalEventStatus.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", nullable = false, updatable = false)
    private Ward ward;

    // NOT a User entity relation — the auth module (where User lives)
    // depends on citizen-registry, so citizen-registry/platform-vital-events
    // cannot depend back on auth without a circular dependency. Same
    // pattern as Citizen.createdBy/archivedBy.
    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Set only when PENDING_APPROVAL -> CAO_REVIEW happens automatically
    // (Governance Tiers §6 — 5 business days unactioned). Null for a
    // deliberate escalation by the Local Body Admin themselves.
    @Column(name = "auto_escalated_at")
    private Instant autoEscalatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.submittedAt == null) this.submittedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
