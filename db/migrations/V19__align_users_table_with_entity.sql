-- V19__align_users_table_with_entity.sql
--
-- FIXES: V1__initial_schema.sql's `users` table (username / password_hash /
-- jurisdiction_type+jurisdiction_id) does not match the columns the auth
-- module's actual JPA `User` entity maps to (email / password / separate
-- ward_id, municipality_id, province_id columns). Login in this codebase
-- is consistently email-based end to end (LoginRequest, AuthService,
-- CustomUserDetailsService, UserRepository all use email) — that's a
-- reasonable, internally-consistent design choice, just not the one the
-- SDD document describes (username-based). Rather than rip out working
-- login logic across 4 files to match the SDD's wording, this migration
-- makes the table a superset that satisfies Hibernate's `ddl-auto:
-- validate` against the entity, while keeping the original SDD-aligned
-- columns in place for anything that still reads them directly.
--
-- NOTE: this leaves both jurisdiction_type/jurisdiction_id (unused by the
-- entity) and ward_id/municipality_id/province_id (used by the entity) on
-- the same table. That's intentional redundancy for now, not a mistake —
-- see the TODO at the bottom for the follow-up cleanup once the team
-- picks one shape permanently.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email               VARCHAR(300),
    ADD COLUMN IF NOT EXISTS password             TEXT,
    ADD COLUMN IF NOT EXISTS ward_id              UUID REFERENCES ward(id),
    ADD COLUMN IF NOT EXISTS municipality_id      UUID REFERENCES municipality(id),
    ADD COLUMN IF NOT EXISTS province_id          UUID REFERENCES province(id),
    ADD COLUMN IF NOT EXISTS enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS account_non_locked   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS failed_attempts      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lock_time            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS password_reset_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_by            UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_at           TIMESTAMPTZ NOT NULL DEFAULT now();

-- email must be unique once populated, but allow NULL during transition
-- (existing seeded rows, if any, may only have `username` set today).
DROP INDEX IF EXISTS uq_users_email;
CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email IS NOT NULL;

-- Exactly one of ward_id / municipality_id / province_id should be set,
-- matching the role, and CENTRAL_ADMIN should have none set (national
-- scope). Enforced here at the DB level so a future bug in account-
-- provisioning code can't silently create a mis-scoped admin.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_single_jurisdiction;
ALTER TABLE users ADD CONSTRAINT chk_users_single_jurisdiction CHECK (
    (role = 'WARD_ADMIN'       AND ward_id IS NOT NULL AND municipality_id IS NULL AND province_id IS NULL) OR
    (role = 'LOCAL_BODY_ADMIN' AND ward_id IS NULL AND municipality_id IS NOT NULL AND province_id IS NULL) OR
    (role = 'PROVINCE_ADMIN'   AND ward_id IS NULL AND municipality_id IS NULL AND province_id IS NOT NULL) OR
    (role = 'CENTRAL_ADMIN'    AND ward_id IS NULL AND municipality_id IS NULL AND province_id IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_users_ward_id         ON users(ward_id);
CREATE INDEX IF NOT EXISTS idx_users_municipality_id ON users(municipality_id);
CREATE INDEX IF NOT EXISTS idx_users_province_id     ON users(province_id);

-- TODO (team decision, not fixed here): pick ONE permanent shape —
-- either drop username/password_hash/jurisdiction_type/jurisdiction_id and
-- fully commit to the entity's email + 3-column design, or migrate the
-- entity to username + jurisdiction_type/jurisdiction_id to match the SDD.
-- Living with both indefinitely is technical debt, not a final state.
