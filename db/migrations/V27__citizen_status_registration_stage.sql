-- V27__citizen_status_registration_stage.sql
--
-- Phase 1 Citizen Core Extensions (Technical System Design — Extended
-- Modules §2.1): introduces `status` as the authoritative field for why a
-- citizen record is or isn't active. `is_active` becomes a column
-- generated from it, so every existing query/index that filters on
-- is_active keeps returning correct results without any application
-- change. Postgres has no "add a generation expression to an existing
-- column" operation, so the column has to be dropped and re-added, which
-- also silently drops every index that references it — all four are
-- recreated below with their original definitions (idx_citizen_active_ward,
-- uq_active_citizen_nid, uq_active_citizen_cit_norm, uq_active_citizen_nid_hmac).
--
-- Also retires `archive_status` (added in V1 for a dual-approval archival
-- workflow) — confirmed unused: zero reads or writes anywhere in the
-- application. `status` supersedes it with a model that's actually wired
-- up (see CitizenService.deactivate()). archived_at/archived_by are kept
-- and repurposed: set whenever status transitions away from ACTIVE.

-- STEP 1 — add the new authoritative status column.
ALTER TABLE citizen ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'DECEASED', 'RENOUNCED_CITIZENSHIP',
                       'VOIDED_DUPLICATE', 'VOIDED_FRAUD'));

-- Backfill: this system has never had a DECEASED or RENOUNCED_CITIZENSHIP
-- path, so any existing is_active = false row only ever got there through
-- the generic admin deactivate endpoint. VOIDED_FRAUD is the closest fit
-- available in the new model for "administratively voided, cause not
-- separately recorded" — reclassify manually if a better record exists.
UPDATE citizen SET status = 'VOIDED_FRAUD' WHERE is_active = FALSE;

-- STEP 2 — drop and recreate is_active as GENERATED, then recreate every
-- index that depended on it.
ALTER TABLE citizen DROP COLUMN is_active;
ALTER TABLE citizen ADD COLUMN is_active BOOLEAN
    GENERATED ALWAYS AS (status = 'ACTIVE') STORED;

CREATE INDEX idx_citizen_active_ward ON citizen(ward_id, is_active);

CREATE UNIQUE INDEX uq_active_citizen_nid
    ON citizen(nid_hash)
    WHERE (is_active = TRUE);

CREATE UNIQUE INDEX uq_active_citizen_cit_norm
    ON citizen(citizenship_no_norm)
    WHERE (is_active = TRUE);

CREATE UNIQUE INDEX uq_active_citizen_nid_hmac
    ON citizen(nid_hmac)
    WHERE (is_active = TRUE AND nid_hmac IS NOT NULL);

-- STEP 3 — remaining Citizen Core Extension fields (Ext. Modules §2.1).
ALTER TABLE citizen ADD COLUMN registration_stage VARCHAR(30) NOT NULL DEFAULT 'DOCUMENT_REGISTERED'
    CHECK (registration_stage IN ('BIRTH_REGISTERED', 'CITIZENSHIP_PENDING', 'DOCUMENT_REGISTERED'));

ALTER TABLE citizen ADD COLUMN birth_registration_no VARCHAR(40) UNIQUE;

ALTER TABLE citizen ADD COLUMN citizenship_type VARCHAR(20)
    CHECK (citizenship_type IN ('CITIZENSHIP', 'NATURALIZED', 'TEMPORARY', 'OTHER'));

ALTER TABLE citizen ADD COLUMN spouse_citizen_id UUID REFERENCES citizen(id);

ALTER TABLE citizen ADD COLUMN marital_status VARCHAR(20) NOT NULL DEFAULT 'SINGLE'
    CHECK (marital_status IN ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED'));

-- STEP 4 — retire the unused archive_status scaffold (see header note).
ALTER TABLE citizen DROP COLUMN archive_status;

COMMENT ON COLUMN citizen.status IS
    'Authoritative lifecycle status. is_active is GENERATED from this column (true only when status = ACTIVE) -- never settable directly, Postgres rejects any write to it.';
