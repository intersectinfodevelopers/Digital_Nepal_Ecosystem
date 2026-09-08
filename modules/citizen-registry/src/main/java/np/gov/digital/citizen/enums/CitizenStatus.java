package np.gov.digital.citizen.enums;

/**
 * Authoritative lifecycle status for a citizen record. {@code isActive} on
 * the entity is a database-generated column derived from this field
 * (true only when status = ACTIVE) — see V27 migration and
 * Citizen#isActive.
 */
public enum CitizenStatus {
    ACTIVE,
    DECEASED,
    RENOUNCED_CITIZENSHIP,
    VOIDED_DUPLICATE,
    VOIDED_FRAUD
}
