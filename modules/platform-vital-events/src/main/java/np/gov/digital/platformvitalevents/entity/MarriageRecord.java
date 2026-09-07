package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 1:1 detail table for a vital_event with event_type = MARRIAGE (SDD
 * Extended Modules §4.4). Deliberately "spouse1"/"spouse2", not "husband"/
 * "wife" — gender-neutral by design. Both spouses must be existing,
 * active citizens; age (>=20) and bigamy are hard-blocked in
 * MarriageRegistrationService, not just validated.
 */
@Entity
@Table(name = "marriage_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarriageRecord {

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

    @Column(name = "marriage_date", nullable = false)
    private LocalDate marriageDate;

    @Column(name = "marriage_place", nullable = false, length = 300)
    private String marriagePlace;

    @Column(name = "witness1_name", length = 300)
    private String witness1Name;

    @Column(name = "witness2_name", length = 300)
    private String witness2Name;

    // Which spouse (if either) relocates to the other's ward — an
    // explicit couple's choice, never assumed. Null means neither
    // relocates.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relocating_citizen_id")
    private Citizen relocatingCitizen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
