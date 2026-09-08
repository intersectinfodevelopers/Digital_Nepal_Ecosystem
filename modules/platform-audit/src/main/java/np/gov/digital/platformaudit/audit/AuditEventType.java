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

    //System
    DATA_EXPORT_ATTEMPTED,
    PERMISSION_DENIED
}