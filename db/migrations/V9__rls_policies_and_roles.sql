-- V9__rls_policies_and_roles.sql
-- REWRITTEN — see SCHEMA_RECONCILIATION_NOTES.md
--
-- Original version created a second, parallel copy of the province/
-- municipality/ward hierarchy under auth.provinces / auth.municipalities /
-- auth.wards, disconnected from the canonical public.province / .municipality
-- / .ward tables created in V1__initial_schema.sql (which the JPA entities in
-- the citizen-registry module actually map to). That meant RLS was being
-- attached to tables the application never reads or writes.
--
-- This version attaches RLS directly to the real V1 tables. No new
-- geographic tables are created here.

-- =============================================================================
-- 1. Application roles — one per government tier
-- =============================================================================

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ward_admin_role') THEN
        CREATE ROLE ward_admin_role;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'local_body_role') THEN
        CREATE ROLE local_body_role;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'province_role') THEN
        CREATE ROLE province_role;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'central_role') THEN
        CREATE ROLE central_role;
    END IF;
END $$;

-- =============================================================================
-- 2. Table-level grants per tier (public schema — the canonical tables)
--    Ward + Local Body : full write on citizen
--    Province + Central: SELECT only — enforced at BOTH grant AND policy level
-- =============================================================================

GRANT SELECT ON province, municipality, ward
    TO ward_admin_role, local_body_role, province_role, central_role;

GRANT SELECT, INSERT, UPDATE ON citizen
    TO ward_admin_role, local_body_role;

GRANT SELECT ON citizen
    TO province_role, central_role;

-- =============================================================================
-- 3. Enable RLS on citizen table
--    FORCE = even the table owner (app DB user) is filtered
-- =============================================================================

ALTER TABLE citizen ENABLE  ROW LEVEL SECURITY;
ALTER TABLE citizen FORCE   ROW LEVEL SECURITY;

-- =============================================================================
-- 4. Drop old policies safely before recreating
-- =============================================================================

DROP POLICY IF EXISTS ward_citizen_policy       ON citizen;
DROP POLICY IF EXISTS local_body_citizen_policy ON citizen;
DROP POLICY IF EXISTS province_citizen_policy   ON citizen;
DROP POLICY IF EXISTS central_citizen_policy    ON citizen;

-- =============================================================================
-- 5. THE 4 RLS POLICIES ON public.citizen
--
--    Session variable             Set by Spring from JWT claim (SET LOCAL
--    app.current_ward_id          → ward_id claim in JWT                      only — never plain
--    app.current_municipality_id  → municipality_id claim                     SET; see platform-
--    app.current_province_id      → province_id claim                        audit's
--                                                                              RlsSessionVariableSetter)
--
--    current_setting('var', true) — the "true" means:
--      return NULL (not ERROR) if the variable is not set.
--      Without it, unauthenticated / non-scoped requests throw an exception.
-- =============================================================================

-- Policy 1 — Ward admin: own ward citizens only
CREATE POLICY ward_citizen_policy
    ON citizen
    AS PERMISSIVE
    FOR ALL
    TO ward_admin_role
    USING (
        ward_id = current_setting('app.current_ward_id', true)::UUID
    );

-- Policy 2 — Local body admin: all wards inside their municipality
CREATE POLICY local_body_citizen_policy
    ON citizen
    AS PERMISSIVE
    FOR ALL
    TO local_body_role
    USING (
        ward_id IN (
            SELECT id FROM ward
            WHERE  municipality_id =
                   current_setting('app.current_municipality_id', true)::UUID
        )
    );

-- Policy 3 — Province admin: SELECT only, all municipalities in their province
CREATE POLICY province_citizen_policy
    ON citizen
    AS PERMISSIVE
    FOR SELECT
    TO province_role
    USING (
        ward_id IN (
            SELECT w.id
            FROM   ward         w
            JOIN   municipality m ON w.municipality_id = m.id
            WHERE  m.province_id =
                   current_setting('app.current_province_id', true)::UUID
        )
    );

-- Policy 4 — Central admin: SELECT only, all citizens nationwide (no filter)
CREATE POLICY central_citizen_policy
    ON citizen
    AS PERMISSIVE
    FOR SELECT
    TO central_role
    USING (true);

-- =============================================================================
-- Note: RLS for citizen_gis, edit_approval, sync_batch, sync_conflict_registry,
-- and citizen_events is added in V18__extend_rls_policies.sql (these tables
-- did not exist yet when V9 originally ran, and citizen_events is created
-- fresh in V15__citizen_events_audit_log.sql).
-- =============================================================================
