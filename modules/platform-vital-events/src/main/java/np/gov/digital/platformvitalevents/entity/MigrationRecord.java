package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;

import java.time.Instant;
import java.util.UUID;

/**
 * 1:1 detail table for a vital_event with event_type = MIGRATION (SDD
 * Extended Modules §4.6) — cross-municipality ward transfer, requiring a
 * genuine two-party losing/receiving Local Body Admin handoff.
 *
 * Two-party confirmation lives here, not on vital_event's own
 * status/reviewedBy/reviewedAt (which only has room for one approver).
 * vital_event only moves PENDING_APPROVAL -&gt; APPROVED once both
 * losingAdminConfirmedAt and receivingAdminConfirmedAt are set — see
 * MigrationRegistrationService.
 */
@Entity
@Table(name = "migration_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationRecord {

    @Id
    @Column(name = "vital_event_id")
    private UUID vitalEventId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vital_event_id")
    private VitalEvent vitalEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "citizen_id", nullable = false)
    private Citizen citizen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_ward_id", nullable = false)
    private Ward fromWard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_ward_id", nullable = false)
    private Ward toWard;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "losing_admin_confirmed_at")
    private Instant losingAdminConfirmedAt;

    @Column(name = "losing_admin_confirmed_by")
    private UUID losingAdminConfirmedBy;

    @Column(name = "receiving_admin_confirmed_at")
    private Instant receivingAdminConfirmedAt;

    @Column(name = "receiving_admin_confirmed_by")
    private UUID receivingAdminConfirmedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    @Transient
    public boolean isFullyConfirmed() {
        return losingAdminConfirmedAt != null && receivingAdminConfirmedAt != null;
    }
}
