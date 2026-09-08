package np.gov.digital.platformgateway.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.platformgateway.enums.AccessOutcome;
import np.gov.digital.platformgateway.enums.ConsentMethod;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Every verify() call, whatever its outcome — the transparency record
 * behind GET /v1/citizens/{id}/access-log (Extended Modules §6.5).
 */
@Entity
@Table(name = "gateway_access_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private Citizen citizen;

    @Column(name = "relying_party_id", nullable = false, updatable = false)
    private UUID relyingPartyId;

    @Column(name = "purpose_code", nullable = false, length = 50, updatable = false)
    private String purposeCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_disclosed", columnDefinition = "jsonb")
    private String fieldsDisclosed;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_method", length = 30)
    private ConsentMethod consentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20, updatable = false)
    private AccessOutcome outcome;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt;

    @PrePersist
    protected void onCreate() {
        if (this.accessedAt == null) this.accessedAt = Instant.now();
    }
}
