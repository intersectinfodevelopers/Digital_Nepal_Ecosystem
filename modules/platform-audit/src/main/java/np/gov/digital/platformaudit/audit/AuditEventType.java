package np.gov.digital.platformaudit.audit;
/**
 *   import np.gov.digitalnepal.platformaudit.audit.AuditEventType;
 */
public enum AuditEventType {

    // Citizen lifecycle
    CITIZEN_REGISTERED,
    CITIZEN_UPDATED,
    CITIZEN_ARCHIVED,
    CITIZEN_RESTORED,

    // Edit approval workflow
    EDIT_SUBMITTED,
    EDIT_APPROVED,
    EDIT_REJECTED,

    // Duplicate / verification
    DUPLICATE_NID_ATTEMPT,
    DUPLICATE_CITIZENSHIP_ATTEMPT,
    NID_VERIFIED,

    // Authentication
    LOGIN_SUCCESS,
    FAILED_LOGIN,
    LOGOUT,
    PASSWORD_CHANGED,
    ACCOUNT_LOCKED,

    // ID card lifecycle
    ID_CARD_INITIATED,
    ID_CARD_APPROVED,
    ID_CARD_REJECTED,
    ID_CARD_COLLECTED,
    ID_CARD_REVOKED,

    // Sync
    SYNC_BATCH_SUBMITTED,
    SYNC_CONFLICT_DETECTED,
    SYNC_CONFLICT_RESOLVED,

    // Grievance
    GRIEVANCE_SUBMITTED,
    GRIEVANCE_RESOLVED,

    // Vital events (SDD Extended Modules §4) — shared across all five
    // event types (birth/death/marriage/divorce/migration); the specific
    // type is in the logged details, not a separate enum value per type,
    // since the workflow itself is identical for all five.
    VITAL_EVENT_SUBMITTED,
    VITAL_EVENT_APPROVED,
    VITAL_EVENT_REJECTED,
    VITAL_EVENT_ESCALATED,

    // Government-to-Person payments (Governance Tiers §8)
    BENEFIT_DISBURSEMENT_INITIATED,
    BENEFIT_DISBURSEMENT_SETTLED,
    BENEFIT_DISBURSEMENT_FAILED,

    //System
    DATA_EXPORT_ATTEMPTED,
    PERMISSION_DENIED
}