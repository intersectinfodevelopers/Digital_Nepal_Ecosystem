package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 1:1 detail table for a vital_event with event_type = DEATH (SDD Extended
 * Modules §4.3). Unlike birth, the subject MUST be an existing citizen —
 * there is no "not registered yet" fallback for a death.
 *
 * ERR_DEATH_ALREADY_RECORDED (a citizen should have at most one non-
 * rejected death_record) is enforced in DeathRegistrationService, not by a
 * DB constraint here — see V34's comment for why a partial unique index
 * doesn't work for this case.
 */
@Entity
@Table(name = "death_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeathRecord {

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

    @Column(name = "date_of_death", nullable = false)
    private LocalDate dateOfDeath;

    @Column(name = "place_of_death", nullable = false, length = 300)
    private String placeOfDeath;

    @Column(name = "immediate_cause_of_death", length = 300)
    private String immediateCauseOfDeath;

    @Column(name = "manner_of_death", length = 20)
    private String mannerOfDeath;

    @Column(name = "informant_name", nullable = false, length = 300)
    private String informantName;

    @Column(name = "informant_relation", nullable = false, length = 100)
    private String informantRelation;

    @Column(name = "certifying_facility", length = 300)
    private String certifyingFacility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
