package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 1:1 detail table for a vital_event with event_type = BIRTH (SDD Extended
 * Modules §4.2). father_citizen_id/mother_citizen_id link directly to an
 * existing citizen when the parent is already registered; the *_name_text
 * fallbacks cover a parent who isn't in the registry yet — one or the
 * other is required for each parent (chk_birth_record_father_identified /
 * chk_birth_record_mother_identified), never both null.
 *
 * childCitizenId is populated only once the event is APPROVED — see
 * BirthRegistrationService.approve(). A newborn has no NID or citizenship
 * number to dedup against, so duplicate detection for an unapproved birth
 * relies on trigram similarity of (child name, DOB, ward) instead — see
 * BirthRecordRepository.findPossibleDuplicates.
 */
@Entity
@Table(name = "birth_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BirthRecord {

    @Id
    @Column(name = "vital_event_id")
    private UUID vitalEventId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vital_event_id")
    private VitalEvent vitalEvent;

    @Column(name = "child_name_np", nullable = false, length = 300)
    private String childNameNp;

    @Column(name = "child_name_en", nullable = false, length = 300)
    private String childNameEn;

    @Column(name = "sex", nullable = false, length = 10)
    private String sex;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "place_of_birth", nullable = false, length = 300)
    private String placeOfBirth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_citizen_id")
    private Citizen fatherCitizen;

    @Column(name = "father_name_text", length = 300)
    private String fatherNameText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mother_citizen_id")
    private Citizen motherCitizen;

    @Column(name = "mother_name_text", length = 300)
    private String motherNameText;

    @Column(name = "birth_weight_kg", precision = 4, scale = 2)
    private java.math.BigDecimal birthWeightKg;

    @Column(name = "delivery_type", length = 20)
    private String deliveryType;

    @Column(name = "attending_facility", length = 300)
    private String attendingFacility;

    // Set on approval — see BirthRegistrationService.approve().
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_citizen_id")
    private Citizen childCitizen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
