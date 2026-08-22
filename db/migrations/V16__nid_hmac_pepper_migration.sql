-- V16__nid_hmac_pepper_migration.sql
--
-- Fixes SDD Critical Implementation Note #2: NID dedup was using plain
-- SHA-256 (nid_hash) instead of HMAC-SHA256 with an application pepper.
-- Plain SHA-256 of a NID is brute-forceable offline if the DB is ever
-- dumped, because NID has a small, guessable format space (10 digits).
-- HMAC with a pepper that lives ONLY in Vault/env (never the DB) makes
-- offline brute-force infeasible even with full DB access.
--
-- This migration adds the new column and constraints alongside the old
-- one. It does NOT drop nid_hash yet — see the backfill note at the
-- bottom. Do not remove nid_hash until the backfill job (run from the
-- application, since the pepper is not available to plain SQL) has
-- populated nid_hmac for every existing active citizen.

ALTER TABLE citizen ADD COLUMN IF NOT EXISTS nid_hmac VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_citizen_nid_hmac ON citizen(nid_hmac);

-- Partial unique index — active citizens only (mirrors the existing
-- uq_active_citizen_nid pattern on nid_hash, allows re-registration
-- after archival).
DROP INDEX IF EXISTS uq_active_citizen_nid_hmac;
CREATE UNIQUE INDEX uq_active_citizen_nid_hmac
    ON citizen(nid_hmac)
    WHERE (is_active = TRUE AND nid_hmac IS NOT NULL);

COMMENT ON COLUMN citizen.nid_hmac IS
    'HMAC-SHA256(nid, pepper) — pepper stored only in Vault/env var (see '
    'app.pepper in application.yml), never in this database. Supersedes '
    'nid_hash. Uniqueness enforced via uq_active_citizen_nid_hmac.';

COMMENT ON COLUMN citizen.nid_hash IS
    'DEPRECATED — plain SHA-256, no pepper. Brute-forceable if DB is '
    'dumped. Kept temporarily for backfill/rollback only. Do not use for '
    'new duplicate-detection logic; use nid_hmac instead. Drop this '
    'column in a follow-up migration once nid_hmac backfill is confirmed '
    'complete for all active citizens and nid_hash is no longer read '
    'anywhere in application code.';

-- =============================================================================
-- BACKFILL NOTE (cannot be done in pure SQL — the pepper is a secret that
-- must never be visible to the database):
--
-- 1. Deploy the updated NidEncryptionUtil (adds hmac(String) method) and
--    CitizenService (writes both nid_hash and nid_hmac on new registrations
--    — see accompanying Java changes).
-- 2. Run a one-off backfill batch job (NOT a Flyway migration) that:
--      a. Reads app.pepper from Vault/env (same source the app already uses).
--      b. For each active citizen with nid_hmac IS NULL, decrypt nid_enc,
--         compute HMAC-SHA256(nid, pepper), write to nid_hmac.
--      c. Never logs the decrypted NID or the pepper.
-- 3. Verify: SELECT count(*) FROM citizen WHERE is_active = TRUE AND
--    nid_hmac IS NULL; -- should be 0 after backfill.
-- 4. Only then, in a later migration, drop nid_hash and its indexes.
-- =============================================================================
