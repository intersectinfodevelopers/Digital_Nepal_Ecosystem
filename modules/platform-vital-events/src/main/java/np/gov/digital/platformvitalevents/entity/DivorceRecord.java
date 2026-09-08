package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 1:1 detail table for a vital_event with event_type = DIVORCE (SDD
 * Extended Modules §4.5). Requires a court order; deliberately never
 * touches residency — no ward transfer here, unlike marriage.
 */
@Entity
@Table(name = "divorce_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DivorceRecord {

    @Id
    @Column(name = "vital_event_id")
    private UUID vitalEventId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vital_event_id")
    private VitalEvent vitalEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spouse1_citizen_id", nullable = false)
    private Citizen spouse1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spouse2_citizen_id", nullable = false)
    private Citizen spouse2;

    @Column(name = "divorce_date", nullable = false)
    private LocalDate divorceDate;

    @Column(name = "court_name", nullable = false, length = 300)
    private String courtName;

    @Column(name = "court_order_no", nullable = false, length = 200)
    private String courtOrderNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
