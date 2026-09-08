package np.gov.digital.platformidcard.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.platformidcard.enums.DocumentStatus;
import np.gov.digital.platformidcard.enums.DocumentType;

import java.time.Instant;
import java.util.UUID;

/**
 * A single persisted document/certificate table shared by ID cards and
 * vital-event certificates (SDD Extended Modules §4.7). See V38's
 * migration comment for why this was built fresh rather than "renamed"
 * from an id_card table that never actually existed.
 *
 * vitalEventId is a plain UUID, not a JPA relation to VitalEvent —
 * platform-idcard doesn't depend on platform-vital-events (that
 * dependency runs the other way: vital-events calls into this module to
 * issue certificates), so there's nothing to map it to here.
 */
@Entity
@Table(name = "official_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficialDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private DocumentType documentType;

    // EAGER, deliberately: every caller of OfficialDocumentService reads
    // the citizen's own fields (name, ward, phone) immediately after
    // getting the document back — for PDF generation or the verify
    // response — and does so from a controller, outside the service's
    // own @Transactional boundary. A LAZY proxy here throws
    // LazyInitializationException the moment anything touches it post-
    // transaction (confirmed live: approving an ID card failed exactly
    // this way before this was changed). Unlike Citizen.ward (LAZY,
    // correctly — not every citizen-reading path needs ward data),
    // official_document's citizen is needed essentially every time.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private Citizen citizen;

    @Column(name = "vital_event_id", updatable = false)
    private UUID vitalEventId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PRINT_PENDING;

    @Column(name = "qr_token", columnDefinition = "TEXT")
    private String qrToken;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "initiated_by", nullable = false, updatable = false)
    private UUID initiatedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revocation_reason", columnDefinition = "TEXT")
    private String revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
