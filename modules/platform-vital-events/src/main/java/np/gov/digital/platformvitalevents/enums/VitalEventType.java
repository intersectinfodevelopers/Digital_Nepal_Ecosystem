package np.gov.digital.platformvitalevents.enums;

/**
 * The five events every ward is the legal registrar for (SDD Extended
 * Modules §4). Each has its own detail table (birth_record, death_record,
 * etc.) joined 1:1 to a vital_event row via vital_event_id — the base
 * table carries the shared submission/approval workflow, the detail table
 * carries the event-specific facts.
 */
public enum VitalEventType {
    BIRTH,
    DEATH,
    MARRIAGE,
    DIVORCE,
    MIGRATION
}
