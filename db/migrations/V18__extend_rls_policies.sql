-- V18__extend_rls_policies.sql
--
-- SDD Section 3.2.2 requires RLS on: citizen (done in V9), citizen_gis,
-- citizen_events, edit_approval (citizen_edit_requests), sync_batch,
-- sync_conflict_registry. Only `citizen` had policies before this migration
-- — the other five were completely unprotected: any authenticated DB
-- connection using ward_admin_role/local_body_role could read GIS points,
-- edit-approval payloads, or sync batches for wards/municipalities outside
-- their own scope, even though the `citizen` table itself was locked down.

-- =============================================================================
-- citizen_gis
-- =============================================================================
ALTER TABLE citizen_gis ENABLE  ROW LEVEL SECURITY;
ALTER TABLE citizen_gis FORCE   ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE ON citizen_gis TO ward_admin_role, local_body_role;
GRANT SELECT ON citizen_gis TO province_role, central_role;

DROP POLICY IF EXISTS ward_gis_policy       ON citizen_gis;
DROP POLICY IF EXISTS local_body_gis_policy ON citizen_gis;
DROP POLICY IF EXISTS province_gis_policy   ON citizen_gis;
DROP POLICY IF EXISTS central_gis_policy    ON citizen_gis;

CREATE POLICY ward_gis_policy ON citizen_gis AS PERMISSIVE FOR ALL
    TO ward_admin_role
    USING (ward_id = current_setting('app.current_ward_id', true)::UUID);

CREATE POLICY local_body_gis_policy ON citizen_gis AS PERMISSIVE FOR ALL
    TO local_body_role
    USING (ward_id IN (
        SELECT id FROM ward
        WHERE municipality_id = current_setting('app.current_municipality_id', true)::UUID
    ));

CREATE POLICY province_gis_policy ON citizen_gis AS PERMISSIVE FOR SELECT
    TO province_role
    USING (ward_id IN (
        SELECT w.id FROM ward w JOIN municipality m ON w.municipality_id = m.id
        WHERE m.province_id = current_setting('app.current_province_id', true)::UUID
    ));

CREATE POLICY central_gis_policy ON citizen_gis AS PERMISSIVE FOR SELECT
    TO central_role USING (true);

-- =============================================================================
-- citizen_edit_requests (edit_approval)
-- =============================================================================
ALTER TABLE citizen_edit_requests ENABLE  ROW LEVEL SECURITY;
ALTER TABLE citizen_edit_requests FORCE   ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE ON citizen_edit_requests TO ward_admin_role, local_body_role;
GRANT SELECT ON citizen_edit_requests TO province_role, central_role;

DROP POLICY IF EXISTS ward_edit_policy       ON citizen_edit_requests;
DROP POLICY IF EXISTS local_body_edit_policy ON citizen_edit_requests;
DROP POLICY IF EXISTS province_edit_policy   ON citizen_edit_requests;
DROP POLICY IF EXISTS central_edit_policy    ON citizen_edit_requests;

CREATE POLICY ward_edit_policy ON citizen_edit_requests AS PERMISSIVE FOR ALL
    TO ward_admin_role
    USING (ward_id = current_setting('app.current_ward_id', true)::UUID);

-- Local Body Admin needs to SEE and APPROVE edits from any ward in their
-- municipality (this is the whole point of the approval tier), so this
-- policy intentionally allows FOR ALL, not just SELECT.
CREATE POLICY local_body_edit_policy ON citizen_edit_requests AS PERMISSIVE FOR ALL
    TO local_body_role
    USING (ward_id IN (
        SELECT id FROM ward
        WHERE municipality_id = current_setting('app.current_municipality_id', true)::UUID
    ));

CREATE POLICY province_edit_policy ON citizen_edit_requests AS PERMISSIVE FOR SELECT
    TO province_role
    USING (ward_id IN (
        SELECT w.id FROM ward w JOIN municipality m ON w.municipality_id = m.id
        WHERE m.province_id = current_setting('app.current_province_id', true)::UUID
    ));

CREATE POLICY central_edit_policy ON citizen_edit_requests AS PERMISSIVE FOR SELECT
    TO central_role USING (true);

-- =============================================================================
-- sync_batch — ward-scoped (Ward Admin submits, Local Body reviews)
-- =============================================================================
ALTER TABLE sync_batch ENABLE  ROW LEVEL SECURITY;
ALTER TABLE sync_batch FORCE   ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE ON sync_batch TO ward_admin_role, local_body_role;
GRANT SELECT ON sync_batch TO province_role, central_role;

DROP POLICY IF EXISTS ward_sync_batch_policy       ON sync_batch;
DROP POLICY IF EXISTS local_body_sync_batch_policy ON sync_batch;

CREATE POLICY ward_sync_batch_policy ON sync_batch AS PERMISSIVE FOR ALL
    TO ward_admin_role
    USING (ward_id = current_setting('app.current_ward_id', true)::UUID);

CREATE POLICY local_body_sync_batch_policy ON sync_batch AS PERMISSIVE FOR ALL
    TO local_body_role
    USING (ward_id IN (
        SELECT id FROM ward
        WHERE municipality_id = current_setting('app.current_municipality_id', true)::UUID
    ));

-- =============================================================================
-- sync_conflict_registry — scoped via citizen -> ward join (no direct
-- ward_id column on this table per the original V7 design)
-- =============================================================================
ALTER TABLE sync_conflict_registry ENABLE  ROW LEVEL SECURITY;
ALTER TABLE sync_conflict_registry FORCE   ROW LEVEL SECURITY;

GRANT SELECT, UPDATE ON sync_conflict_registry TO local_body_role;
GRANT SELECT ON sync_conflict_registry TO province_role, central_role;

DROP POLICY IF EXISTS local_body_conflict_policy ON sync_conflict_registry;

-- Conflict resolution is a Local Body Admin action per the SDD (Ward Admin
-- does not resolve conflicts directly) — scoped to their own municipality
-- via the citizen the conflict belongs to.
CREATE POLICY local_body_conflict_policy ON sync_conflict_registry AS PERMISSIVE FOR ALL
    TO local_body_role
    USING (citizen_id IN (
        SELECT c.id FROM citizen c
        WHERE c.ward_id IN (
            SELECT id FROM ward
            WHERE municipality_id = current_setting('app.current_municipality_id', true)::UUID
        )
    ));

-- =============================================================================
-- citizen_events — READ ACCESS ONLY via RLS (writes go through the
-- application service layer + the append-only trigger from V15, never
-- direct client SQL). Ward/Local Body see their own jurisdiction's events;
-- Central sees everything (full audit visibility, per SDD Section 9.1).
-- =============================================================================
ALTER TABLE citizen_events ENABLE  ROW LEVEL SECURITY;
ALTER TABLE citizen_events FORCE   ROW LEVEL SECURITY;

GRANT SELECT ON citizen_events TO ward_admin_role, local_body_role, province_role, central_role;

DROP POLICY IF EXISTS ward_events_policy       ON citizen_events;
DROP POLICY IF EXISTS local_body_events_policy ON citizen_events;
DROP POLICY IF EXISTS province_events_policy   ON citizen_events;
DROP POLICY IF EXISTS central_events_policy    ON citizen_events;

CREATE POLICY ward_events_policy ON citizen_events AS PERMISSIVE FOR SELECT
    TO ward_admin_role
    USING (jurisdiction_id = current_setting('app.current_ward_id', true)::UUID);

CREATE POLICY local_body_events_policy ON citizen_events AS PERMISSIVE FOR SELECT
    TO local_body_role
    USING (jurisdiction_id = current_setting('app.current_municipality_id', true)::UUID
           OR jurisdiction_id IN (
               SELECT id FROM ward
               WHERE municipality_id = current_setting('app.current_municipality_id', true)::UUID
           ));

CREATE POLICY province_events_policy ON citizen_events AS PERMISSIVE FOR SELECT
    TO province_role
    USING (jurisdiction_id = current_setting('app.current_province_id', true)::UUID);

CREATE POLICY central_events_policy ON citizen_events AS PERMISSIVE FOR SELECT
    TO central_role USING (true);
