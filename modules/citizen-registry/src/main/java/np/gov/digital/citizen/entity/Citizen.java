package np.gov.digital.citizen.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.enums.CitizenStatus;
import np.gov.digital.citizen.enums.CitizenshipType;
import np.gov.digital.citizen.enums.ConsentChannel;
import np.gov.digital.citizen.enums.DigitalLiteracy;
import np.gov.digital.citizen.enums.MaritalStatus;
import np.gov.digital.citizen.enums.RegistrationStage;
import np.gov.digital.citizen.enums.SyncStatus;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

// Core citizen identity entity for the Digital Nepal Citizen Ecosystem.
@Entity
@Table(
        name = "citizen",
        indexes = {
                @Index(name = "idx_citizen_ward", columnList = "ward_id"),
                @Index(name = "idx_citizen_nid_hash", columnList = "nid_hash"),
                @Index(name = "idx_citizen_cit_norm", columnList = "citizenship_no_norm"),
                @Index(name = "idx_citizen_active_ward", columnList = "ward_id, is_active")
        }
)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Citizen {
    // Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Geographic scope - RLS boundary
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ward_id", nullable = false)
    private Ward ward;

    // Identity - PII fields
    // Reference into identity_vault (Extended Modules §2.3) — the citizen
    // row never holds NID ciphertext directly. Resolve via
    // IdentityVaultService, not by reading this column's target row
    // yourself.
    @Column(name = "nid_ref")
    private UUID nidRef;

    // DEPRECATED — plain SHA-256 hash of the plaintext NID, no pepper.
    // Kept only during the V16 backfill transition. New code should read/
    // write nidHmac instead.
    //
    // Nullable since V33: a citizen registered via an approved birth
    // event (registrationStage = BIRTH_REGISTERED) has no NID yet by
    // design — this column being NOT NULL since V1 would otherwise make
    // that impossible to insert at all.
    @Deprecated
    @Column(name = "nid_hash", length = 64)
    private String nidHash;

    // HMAC-SHA256(nid, pepper) — the correct dedup mechanism. Pepper lives
    // only in Vault/env (NidEncryptionUtil), never in this column or table.
    @Column(name = "nid_hmac", length = 64)
    private String nidHmac;

    // Reference into identity_vault (Extended Modules §2.3) — same pattern
    // as nidRef.
    @Column(name = "citizenship_ref")
    private UUID citizenshipRef;

    // Alphanumeric-sanitized citizenship number (dashes and slashes stripped).
    // Internal-only — scoped to the family-link join, never returned by any
    // API. Duplicate detection uses citizenshipHmac instead (V28).
    //
    // Nullable since V33 — same reason as nidHash above: a birth-registered
    // citizen has no citizenship certificate yet either.
    @Column(name = "citizenship_no_norm", length = 100)
    private String citizenshipNoNorm;

    // HMAC-SHA256(citizenshipNo, pepper) — independent dedup from nid_hmac;
    // either document can block a duplicate registration on its own.
    @Column(name = "citizenship_hmac", length = 64)
    private String citizenshipHmac;

    // AES-256/GCM encrypted passport number. Nullable — not all citizens have passports.
    @Column(name = "passport_no_enc")
    private String passportNoEnc;

    // NAME — NOT encrypted (displayed on ID cards and dashboards)
    @Column(name = "name_np", nullable = false, length = 300)
    private String nameNp;

    @Column(name = "name_en", nullable = false, length = 300)
    private String nameEn;

    // BASIC DEMOGRAPHICS
    @Column(name = "dob_enc", nullable = false)
    private String dobEnc;

    /** MALE / FEMALE / OTHER */
    @Column(name = "sex", nullable = false, length = 10)
    private String sex;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(name = "religion", length = 100)
    private String religion;

    @Column(name = "ethnicity", length = 100)
    private String ethnicity;

    @Column(name = "mother_tongue", length = 100)
    private String motherTongue;

    // Sub-ward location name (tole/settlement)
    @Column(name = "tole", length = 200)
    private String tole;

    // CONTACT — encrypted
    @Column(name = "phone_enc")
    private String phoneEnc;

    @Column(name = "phone_alt_enc")
    private String phoneAltEnc;

    @Column(name = "email_enc")
    private String emailEnc;

    // DIGITAL PROFILE
    @Enumerated(EnumType.STRING)
    @Column(name = "digital_literacy", length = 20)
    private DigitalLiteracy digitalLiteracy;

    @Column(name = "has_smartphone")
    @Builder.Default
    private Boolean hasSmartphone = false;

    @Column(name = "photo_url")
    private String photoUrl;

    // NID VERIFICATION STATUS
    @Column(name = "nid_verified")
    @Builder.Default
    private Boolean nidVerified = false;

    @Column(name = "is_async_verified")
    @Builder.Default
    private Boolean isAsyncVerified = false;

    @Column(name = "nid_verified_at")
    private Instant nidVerifiedAt;

    // CONSENT — required by Individual Privacy Act 2018 + Constitution Art. 28
    @Column(name = "consent_recorded_at", nullable = false)
    private Instant consentRecordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_channel", nullable = false, length = 50)
    private ConsentChannel consentChannel;

    // OFFLINE SYNC TRACKING
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", length = 20)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    @Column(name = "local_record_id")
    private UUID localRecordId;

    // Device that submitted this record (for audit and conflict tracing).
    @Column(name = "device_id", length = 200)
    private String deviceId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    // WARD_OFFICE / FIELD / PORTAL / MOBILE
    @Column(name = "registration_channel", length = 50)
    private String registrationChannel;

    // OPTIMISTIC LOCKING — offline conflict detection
    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    // LIFECYCLE STATUS (V27 / Extended Modules §2.1) — the authoritative
    // field for why a citizen record is or isn't active. Supersedes the
    // old archive_status column, which was never actually wired to any
    // service logic.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CitizenStatus status = CitizenStatus.ACTIVE;

    // GENERATED ALWAYS AS (status = 'ACTIVE') STORED — read-only from
    // Hibernate's side. @Generated tells Hibernate to re-SELECT this
    // column after every INSERT/UPDATE rather than trust whatever value
    // (if any) is sitting in the Java field.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "is_active", insertable = false, updatable = false)
    private Boolean isActive;

    // PROGRESSIVE IDENTITY (Concept Document §4 / Extended Modules §2.1)
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_stage", nullable = false, length = 30)
    @Builder.Default
    private RegistrationStage registrationStage = RegistrationStage.DOCUMENT_REGISTERED;

    @Column(name = "birth_registration_no", length = 40, unique = true)
    private String birthRegistrationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "citizenship_type", length = 20)
    private CitizenshipType citizenshipType;

    // Not a JPA relationship — plain FK reference, same pattern as
    // createdBy/archivedBy. Set/cleared by the (future) marriage/divorce
    // vital-event workflow, not by ordinary citizen CRUD.
    @Column(name = "spouse_citizen_id")
    private UUID spouseCitizenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", nullable = false, length = 20)
    @Builder.Default
    private MaritalStatus maritalStatus = MaritalStatus.SINGLE;

    @Column(name = "archived_at")
    private Instant archivedAt;

    // archived_by references users.id — will be a UUID FK once User entity exists
    @Column(name = "archived_by")
    private UUID archivedBy;

    // AUDIT FIELDS
    // created_by references users.id
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // LIFECYCLE HOOKS
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.versionNumber == null) this.versionNumber = 1;
        if (this.syncStatus == null) this.syncStatus = SyncStatus.SYNCED;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
