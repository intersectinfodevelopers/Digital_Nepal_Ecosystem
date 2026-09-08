package np.gov.digital.platformgateway.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;

import java.time.Instant;
import java.util.UUID;

/**
 * Aadhaar-style pairwise reference token (Extended Modules §6.3) —
 * unique per (citizen, relying party), so two different relying parties
 * can never correlate the same citizen via a shared identifier.
 */
@Entity
@Table(name = "citizen_relying_party_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenRelyingPartyToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private Citizen citizen;

    @Column(name = "relying_party_id", nullable = false, updatable = false)
    private UUID relyingPartyId;

    @Builder.Default
    @Column(name = "token", nullable = false, unique = true, updatable = false)
    private UUID token = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
