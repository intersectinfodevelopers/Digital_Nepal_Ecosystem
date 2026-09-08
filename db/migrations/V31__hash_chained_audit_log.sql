-- V31__hash_chained_audit_log.sql
--
-- Phase 2 (Technical System Design — Extended Modules §5.2): tamper-evident
-- hash-chained audit logging. Each citizen_events row's entry_hash covers
-- its own content plus the previous row's entry_hash, so altering any past
-- row breaks every subsequent hash — detectable even by a user with full
-- table UPDATE rights (application_role doesn't have those anyway; see the
-- REVOKE in V15).
--
-- Deviation from the design doc's own example: its trigger chains strictly
-- by `WHERE citizen_id = NEW.citizen_id`, but citizen_id is nullable here
-- (some events — WARD_ADMIN_CREATED etc. — are about a user account, not a
-- citizen). SQL's NULL = NULL is never true, so a literal implementation
-- would make every non-citizen event its own unchained one-row "chain" —
-- undermining the whole point for exactly the events that don't happen to
-- have a citizen_id. Fixed with IS NOT DISTINCT FROM (Postgres's null-safe
-- equality), which correctly groups all NULL-citizen_id events into one
-- shared chain instead.

ALTER TABLE citizen_events ADD COLUMN entry_hash CHAR(64);
ALTER TABLE citizen_events ADD COLUMN previous_entry_hash CHAR(64);

-- Backfill existing rows before the trigger takes over, in the same
-- deterministic order the trigger will use going forward (created_at, id)
-- and with the same NULL-safe per-citizen grouping.
DO $$
DECLARE
    rec RECORD;
    prev_hash CHAR(64);
    chain_key TEXT;
    last_hash_per_key JSONB := '{}'::JSONB;
BEGIN
    FOR rec IN
        SELECT id, citizen_id, event_type, old_value_json, new_value_json, acted_by, created_at
        FROM citizen_events
        ORDER BY created_at, id
    LOOP
        chain_key := COALESCE(rec.citizen_id::TEXT, '__none__');
        prev_hash := last_hash_per_key ->> chain_key;
        IF prev_hash IS NULL THEN
            prev_hash := repeat('0', 64);
        END IF;

        UPDATE citizen_events
        SET previous_entry_hash = prev_hash,
            entry_hash = encode(digest(
                concat_ws('|', rec.citizen_id, rec.event_type, rec.old_value_json::text,
                          rec.new_value_json::text, rec.acted_by, rec.created_at, prev_hash),
                'sha256'), 'hex')
        WHERE id = rec.id AND created_at = rec.created_at;

        last_hash_per_key := jsonb_set(
            last_hash_per_key, ARRAY[chain_key],
            to_jsonb((SELECT entry_hash FROM citizen_events WHERE id = rec.id AND created_at = rec.created_at))
        );
    END LOOP;
END $$;

ALTER TABLE citizen_events ALTER COLUMN entry_hash SET NOT NULL;
ALTER TABLE citizen_events ALTER COLUMN previous_entry_hash SET NOT NULL;

CREATE OR REPLACE FUNCTION fn_hash_chain_citizen_events() RETURNS TRIGGER AS $$
DECLARE
    prev_hash CHAR(64);
BEGIN
    SELECT entry_hash INTO prev_hash FROM citizen_events
    WHERE citizen_id IS NOT DISTINCT FROM NEW.citizen_id
    ORDER BY created_at DESC, id DESC LIMIT 1;

    IF prev_hash IS NULL THEN
        prev_hash := repeat('0', 64);
    END IF;

    NEW.previous_entry_hash := prev_hash;
    NEW.entry_hash := encode(digest(
        concat_ws('|', NEW.citizen_id, NEW.event_type, NEW.old_value_json::text,
                  NEW.new_value_json::text, NEW.acted_by, NEW.created_at, prev_hash),
        'sha256'), 'hex');

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_hash_chain_citizen_events ON citizen_events;
CREATE TRIGGER trg_hash_chain_citizen_events
    BEFORE INSERT ON citizen_events
    FOR EACH ROW EXECUTE FUNCTION fn_hash_chain_citizen_events();

COMMENT ON COLUMN citizen_events.entry_hash IS
    'SHA-256(row content || previous_entry_hash) -- set only by trg_hash_chain_citizen_events, never by application code.';
