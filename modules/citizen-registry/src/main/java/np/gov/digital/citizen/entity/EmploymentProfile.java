package np.gov.digital.citizen.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
 
@Entity(name = "CitizenEmploymentProfile")
@Table(name = "employment_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmploymentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false, unique = true)
    private Citizen citizen;

    /**
     * One of 10 employment categories:
     * UNEMPLOYED / SELF_EMPLOYED / FARMER / GOVERNMENT /
     * PRIVATE / FOREIGN / STUDENT / HOMEMAKER / RETIRED / DISABLED_UNABLE
     */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /**
     * Category-specific sub-fields stored as JSON string.
     * Example for UNEMPLOYED: {"duration_months": 6, "last_employer": "ABC Co"}
     *
     * BUG FIX: columnDefinition = "jsonb" alone doesn't tell Hibernate how
     * to bind the parameter — a plain String still goes over the wire as
     * VARCHAR, which Postgres rejects on every write ("column is of type
     * jsonb but expression is of type character varying"). Currently
     * nothing in this codebase writes to subFields yet, so this was
     * dormant rather than actively broken — but it would have failed the
     * moment anyone did. See the sibling np.gov.digital.employment.entity.
     * EmploymentProfile (a separate class mapped to this same table) for
     * where @JdbcTypeCode(SqlTypes.JSON) was already present, and
     * platform-vital-events' VerbalAutopsyResponse for where this exact
     * bug pattern was first reproduced live.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sub_fields", columnDefinition = "jsonb")
    private String subFields;

    @Column(name = "income_band", length = 30)
    private String incomeBand;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // updated_by references users.id
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isUnemployed() {
        return "UNEMPLOYED".equalsIgnoreCase(this.category);
    }
}
