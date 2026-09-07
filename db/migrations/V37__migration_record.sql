-- V37__migration_record.sql
--
-- Phase 2 (SDD Extended Modules §4.6): cross-municipality ward transfer,
-- requiring a genuine two-party losing/receiving Local Body Admin handoff
-- — distinct from every other vital event type, which has a single
-- approver.

CREATE TABLE migration_record (
    vital_event_id                UUID PRIMARY KEY REFERENCES vital_event(id) ON DELETE CASCADE,

    citizen_id                    UUID NOT NULL REFERENCES citizen(id),
    from_ward_id                  UUID NOT NULL REFERENCES ward(id),
    to_ward_id                    UUID NOT NULL REFERENCES ward(id),
    CONSTRAINT chk_migration_record_distinct_wards CHECK (from_ward_id <> to_ward_id),

    reason                        VARCHAR(300),

    -- Two-party confirmation — NOT vital_event.status/reviewed_by/
    -- reviewed_at, which only has room for one approver. vital_event
    -- itself only moves PENDING_APPROVAL -> APPROVED once BOTH of these
    -- are set (enforced in MigrationRegistrationService, not a DB
    -- trigger). Each admin must belong to the respective municipality —
    -- also enforced in the service layer, since it needs to compare
    -- against the actor's own municipality_id, not just their role.
    losing_admin_confirmed_at     TIMESTAMPTZ,
    losing_admin_confirmed_by     UUID REFERENCES users(id),
    receiving_admin_confirmed_at  TIMESTAMPTZ,
    receiving_admin_confirmed_by  UUID REFERENCES users(id),

    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_migration_record_citizen ON migration_record(citizen_id);

-- ERR_TRANSFER_PENDING (SDD Extended Modules §10): a citizen shouldn't
-- have two migration requests in flight at once. Enforced in
-- MigrationRegistrationService against vital_event.status, not a DB
-- constraint — same reasoning as every other cross-table "is there
-- already an active one of these" check in this migration series
-- (death's ERR_DEATH_ALREADY_RECORDED, marriage's bigamy check).
