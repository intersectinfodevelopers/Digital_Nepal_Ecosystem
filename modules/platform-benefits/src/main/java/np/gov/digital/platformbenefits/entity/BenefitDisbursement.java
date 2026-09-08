package np.gov.digital.platformbenefits.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.platformbenefits.enums.BenefitType;
import np.gov.digital.platformbenefits.enums.PaymentRail;
import np.gov.digital.platformbenefits.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Government-to-Person cash disbursement (Governance Tiers §8). See
 * V39's migration comment for why this was built fresh rather than
 * "gaining payment_rail columns" — there was no benefit_disbursement
 * table to add them to.
 *
 * citizen EAGER, same reasoning as platform-idcard's OfficialDocument:
 * every caller reads the citizen's own fields (for the eligible-list
 * view, for a payment-rail-specific payout call) outside this module's
 * own @Transactional boundary, and a LAZY proxy accessed post-
 * transaction throws LazyInitializationException — confirmed as a real,
 * reproducible bug in OfficialDocument before it was fixed there; not
 * repeating it here.
 */
@Entity
@Table(name = "benefit_disbursement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitDisbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private Citizen citizen;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false, length = 20, updatable = false)
    private BenefitType benefitType;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period;

    @Column(name = "amount_npr", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amountNpr;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_rail", nullable = false, length = 20, updatable = false)
    private PaymentRail paymentRail;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "external_reference", length = 200)
    private String externalReference;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "initiated_by", nullable = false, updatable = false)
    private UUID initiatedBy;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.initiatedAt == null) this.initiatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
