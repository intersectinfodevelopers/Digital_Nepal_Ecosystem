-- V17__citizen_edit_requests_table.sql
--
-- FIXES A MISSING TABLE: the `auth` module's CitizenEditRequest entity
-- (ApprovalService / ApprovalController) maps to a table called
-- citizen_edit_requests, but no migration anywhere created it. This is the
-- SDD's edit_approval state machine (Section 5.1) — a P0 feature. Without
-- this table, every PUT /citizens/{id} edit submission would fail at
-- runtime against a real (non-Hibernate-auto-ddl) database.

CREATE TABLE IF NOT EXISTS citizen_edit_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id          UUID NOT NULL REFERENCES citizen(id),

    -- Denormalized for RLS — kept in sync by the trigger below.
    -- Never trust a client-supplied ward_id here.
    ward_id             UUID NOT NULL REFERENCES ward(id),

    submitted_by        UUID NOT NULL REFERENCES users(id),
    approved_by         UUID REFERENCES users(id),

    old_value_json      JSONB,
    change_payload      JSONB NOT NULL,   -- proposed new values
    rejection_reason    TEXT,

    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','CAO_REVIEW')),

    escalated_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at         TIMESTAMPTZ,

    -- Prevent self-approval — a hard DB-level guarantee, not just an
    -- application-layer check that a future refactor could accidentally drop.
    CONSTRAINT no_self_approval CHECK (submitted_by <> approved_by)
);

CREATE INDEX IF NOT EXISTS idx_edit_requests_citizen ON citizen_edit_requests(citizen_id);
CREATE INDEX IF NOT EXISTS idx_edit_requests_ward     ON citizen_edit_requests(ward_id);
CREATE INDEX IF NOT EXISTS idx_edit_requests_status
    ON citizen_edit_requests(status) WHERE status = 'PENDING_APPROVAL';
CREATE INDEX IF NOT EXISTS idx_edit_requests_escalation
    ON citizen_edit_requests(escalated_at) WHERE status = 'PENDING_APPROVAL';

-- Auto-populate ward_id from the citizen row at insert time, so the
-- application never has to (and never can) supply it directly — closes
-- off a class of "spoof the ward_id to smuggle an edit past RLS" bugs.
CREATE OR REPLACE FUNCTION citizen_edit_requests_set_ward_id()
RETURNS TRIGGER AS $$
BEGIN
    SELECT ward_id INTO NEW.ward_id FROM citizen WHERE id = NEW.citizen_id;
    IF NEW.ward_id IS NULL THEN
        RAISE EXCEPTION 'citizen_edit_requests: citizen % has no ward_id', NEW.citizen_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_edit_requests_set_ward ON citizen_edit_requests;
CREATE TRIGGER trg_edit_requests_set_ward
    BEFORE INSERT ON citizen_edit_requests
    FOR EACH ROW EXECUTE FUNCTION citizen_edit_requests_set_ward_id();
