package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.enums.MaritalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per citizen per vital_event that changed their marital status
 * (SDD Extended Modules §4.4) — a marriage writes two rows (one per
 * spouse), a future divorce likewise. Also captures the ward transfer, if
 * any, that came with the status change.
 */
@Entity
@Table(name = "marital_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaritalStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "citizen_id", nullable = false)
    private UUID citizenId;

    @Column(name = "vital_event_id", nullable = false)
    private UUID vitalEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_marital_status", length = 20)
    private MaritalStatus previousMaritalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_marital_status", nullable = false, length = 20)
    private MaritalStatus newMaritalStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_ward_id")
    private Ward previousWard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_ward_id")
    private Ward newWard;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
