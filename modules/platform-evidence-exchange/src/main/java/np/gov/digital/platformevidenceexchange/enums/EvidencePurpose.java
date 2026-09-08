package np.gov.digital.platformevidenceexchange.enums;

/**
 * Why a fact is being requested — distinct from fact_requested (the
 * free-text specifics of what's being asked), since purpose is what
 * access-control decisions key off, not the exact wording of the
 * question. A DB-level VARCHAR rather than a CHECK-constrained column
 * (see V40) so a new purpose can be added without a migration; this
 * enum is still the source of truth for what's currently a valid value
 * at the API boundary.
 */
public enum EvidencePurpose {
    CITIZENSHIP_VERIFICATION,
    NID_VERIFICATION,
    VOTER_ROLL_CHECK
}
