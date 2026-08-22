package np.gov.digital.citizen.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "disability_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisabilityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false, unique = true)
    private Citizen citizen;

    /**
     * PHYSICAL / SENSORY_VISION / SENSORY_HEARING /
     * INTELLECTUAL / PSYCHOSOCIAL / SPEECH / MULTIPLE
     */
    @Column(name = "disability_type", nullable = false, length = 50)
    private String disabilityType;

    /** WHO ICF body function severity: 0-4 */
    @Column(name = "severity_body", nullable = false)
    private Short severityBody;

    /** WHO ICF activity limitation severity: 0-4 */
    @Column(name = "severity_activity", nullable = false)
    private Short severityActivity;

    /** WHO ICF participation restriction severity: 0-4 */
    @Column(name = "severity_participation", nullable = false)
    private Short severityParticipation;

    /** Certificate number — must be present for Disability ID card eligibility */
    @Column(name = "certificate_no", length = 100)
    private String certificateNo;

    @Column(name = "issuing_hospital", length = 300)
    private String issuingHospital;

    @Column(name = "certificate_date")
    private LocalDate certificateDate;

    @Column(name = "certificate_expiry")
    private LocalDate certificateExpiry;

    @Column(name = "assistive_device", length = 100)
    private String assistiveDevice;

    @Column(name = "device_provided")
    @Builder.Default
    private Boolean deviceProvided = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the maximum severity across all three WHO ICF dimensions.
     * Used by EligibilityService for disability ID card rule evaluation.
     */
    public int getMaxSeverity() {
        return Math.max(severityBody, Math.max(severityActivity, severityParticipation));
    }

    /**
     * Returns true if a disability certificate is present.
     * Required for Disability ID card eligibility.
     */
    public boolean hasCertificate() {
        return certificateNo != null && !certificateNo.isBlank();
    }
}
