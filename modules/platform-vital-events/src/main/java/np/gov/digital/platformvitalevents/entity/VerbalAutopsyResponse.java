package np.gov.digital.platformvitalevents.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * WHO 2016 Verbal Autopsy instrument responses for a death outside a
 * health facility with no other certified cause (SDD Extended Modules
 * §4.3). Optional, and attachable before or after the death event itself
 * is approved/rejected — it doesn't participate in vital_event's state
 * machine.
 *
 * `responses` holds the raw instrument answers as a JSON string (same
 * convention as CitizenEditRequest.changePayload) rather than one column
 * per question — the real WHO instrument is ~100 branching questions
 * across several age-specific modules, which would make column-per-field
 * modelling (and every future instrument revision) unmanageable.
 */
@Entity
@Table(name = "verbal_autopsy_response")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerbalAutopsyResponse {

    @Id
    @Column(name = "vital_event_id")
    private UUID vitalEventId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vital_event_id")
    private DeathRecord deathRecord;

    @Column(name = "respondent_name", nullable = false, length = 300)
    private String respondentName;

    @Column(name = "respondent_relation", nullable = false, length = 100)
    private String respondentRelation;

    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;

    // BUG FIX (found live): columnDefinition = "jsonb" alone is not enough
    // — Hibernate still binds a plain String parameter as VARCHAR unless
    // told otherwise, which Postgres rejects outright ("column is of type
    // jsonb but expression is of type character varying") on every
    // INSERT. @JdbcTypeCode(SqlTypes.JSON) is the fix — see
    // SyncRecord.payload in platform-sync for the one other place in this
    // codebase that already does this correctly. CitizenEditRequest and
    // SyncConflictRegistry had the same bug and are fixed alongside this.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responses", nullable = false, columnDefinition = "jsonb")
    private String responses;

    // Filled in later once a physician (or physician-reviewed algorithm)
    // codes the raw responses into an ICD-10 cause.
    @Column(name = "probable_cause_of_death", length = 300)
    private String probableCauseOfDeath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
