-- V15__citizen_events_audit_log.sql
--
-- Replaces the empty placeholder previously left in V2__create_audit_and_logs.sql.
-- Implements the append-only audit log described in SDD Section 4.13 and
-- Critical Implementation Note #4 ("citizen_events Is Append-Only — Revoke
-- DELETE/UPDATE").
--
-- This table is the permanent forensic record of every sensitive action in
-- the system: citizen registration/edits, ID card issuance, grievance filing,
-- admin account creation (WARD_ADMIN_CREATED etc.), password resets. It must
-- never be editable by the application, only appendable.

CREATE TABLE IF NOT EXISTS citizen_events (
    id               UUID NOT NULL DEFAULT gen_random_uuid(),

    -- Nullable: some events (e.g. WARD_ADMIN_CREATED, LOCAL_BODY_ADMIN_CREATED)
    -- are about a user account, not a citizen record.
    citizen_id       UUID REFERENCES citizen(id),

    -- Examples: CITIZEN_REGISTERED, CITIZEN_EDITED, EDIT_APPROVED, EDIT_REJECTED,
    -- ID_CARD_ISSUED, ID_CARD_REVOKED, GRIEVANCE_FILED, GRIEVANCE_ESCALATED,
    -- WARD_ADMIN_CREATED, LOCAL_BODY_ADMIN_CREATED, PROVINCE_ADMIN_CREATED,
    -- ADMIN_ACCOUNT_DISABLED, PASSWORD_RESET, LOGIN_FAILED, LOGIN_LOCKED,
    -- DATA_PURGED, DATA_EXPORTED
    event_type       VARCHAR(80)  NOT NULL,

    old_value_json   JSONB,
    new_value_json   JSONB,

    -- The account that performed the action. NOT NULL — every event must be
    -- attributable. System-triggered events (Quartz jobs) use a reserved
    -- system-service account id, never NULL.
    acted_by         UUID         NOT NULL REFERENCES users(id),
    acted_role       VARCHAR(50)  NOT NULL,

    -- Geographic scope the actor held at the time of the action (their
    -- ward_id / municipality_id / province_id, whichever applies to their
    -- role) — lets Local Body Admin filter "my municipality's events" and
    -- Central Admin filter/aggregate nationally.
    jurisdiction_id  UUID         NOT NULL,

    -- SHA-256 of the request IP — never store plaintext IP (privacy).
    ip_hash          VARCHAR(64),
    device_id        VARCHAR(200),

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Annual partitions. Add the next year's partition well before the
-- current one rolls over (a Quartz job or ops runbook should own this;
-- do not let INSERTs fail because a partition is missing).
CREATE TABLE IF NOT EXISTS citizen_events_2026 PARTITION OF citizen_events
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS citizen_events_2027 PARTITION OF citizen_events
    FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');

CREATE INDEX IF NOT EXISTS idx_events_citizen      ON citizen_events(citizen_id);
CREATE INDEX IF NOT EXISTS idx_events_type         ON citizen_events(event_type);
CREATE INDEX IF NOT EXISTS idx_events_created      ON citizen_events(created_at);
CREATE INDEX IF NOT EXISTS idx_events_jurisdiction ON citizen_events(jurisdiction_id);
CREATE INDEX IF NOT EXISTS idx_events_acted_by     ON citizen_events(acted_by);

-- =============================================================================
-- APPEND-ONLY ENFORCEMENT
--
-- Two layers, deliberately redundant:
--   1. Revoke UPDATE/DELETE from the application's own DB role, so even a
--      SQL-injection vulnerability elsewhere in the app cannot tamper with
--      history through the app's normal DB credentials.
--   2. A BEFORE UPDATE/DELETE trigger that raises an exception unconditionally,
--      so even a superuser session that forgets about the REVOKE (e.g. after
--      a role grant mistake) still cannot silently mutate the log without
--      explicitly disabling the trigger first (itself a loud, logged action).
--
-- Replace 'app_role' below with whatever your Spring Boot datasource
-- connects as in application.yml (spring.datasource.username).
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_role') THEN
        EXECUTE 'REVOKE UPDATE, DELETE ON citizen_events FROM app_role';
        EXECUTE 'REVOKE UPDATE, DELETE ON citizen_events_2026 FROM app_role';
        EXECUTE 'REVOKE UPDATE, DELETE ON citizen_events_2027 FROM app_role';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION citizen_events_block_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'citizen_events is append-only. UPDATE/DELETE is not permitted (attempted % on id=%). '
        'Only a superuser may purge old partitions, and only after Board approval per SDD 3A Critical #4.',
        TG_OP, OLD.id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_citizen_events_no_update ON citizen_events;
CREATE TRIGGER trg_citizen_events_no_update
    BEFORE UPDATE OR DELETE ON citizen_events
    FOR EACH ROW EXECUTE FUNCTION citizen_events_block_mutation();

-- A separate read-only audit role can SELECT (per SDD 3A Critical #4).
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'audit_readonly_role') THEN
        CREATE ROLE audit_readonly_role;
    END IF;
END $$;
GRANT SELECT ON citizen_events, citizen_events_2026, citizen_events_2027
    TO audit_readonly_role;
