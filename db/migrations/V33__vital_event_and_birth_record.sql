-- V33__vital_event_and_birth_record.sql
--
-- Phase 2 (SDD Extended Modules §4.1-4.2): the shared vital_event base
-- table + state machine, and the first concrete event type, birth
-- registration.

-- ---------------------------------------------------------------------
-- PRE-REQUISITE FIX: a newborn registered via a birth event has neither
-- a National ID nor a citizenship certificate yet (that's the entire
-- point of RegistrationStage.BIRTH_REGISTERED, added in V27) — but
-- citizen.nid_hash and citizen.citizenship_no_norm have been NOT NULL
-- since V1, which would make it impossible to ever insert such a citizen
-- row. Every other identity column added since (nid_hmac, nid_ref,
-- citizenship_hmac, citizenship_ref) is already nullable for exactly
-- this reason; these two legacy columns were simply never revisited.
-- Confirmed safe: uq_active_citizen_nid / uq_active_citizen_cit_norm are
-- plain (non "... IS NOT NULL") partial unique indexes, but Postgres
-- unique indexes never treat two NULLs as equal, so any number of active
-- citizens with NULL nid_hash/citizenship_no_norm can coexist without
-- violating them.
ALTER TABLE citizen ALTER COLUMN nid_hash DROP NOT NULL;
ALTER TABLE citizen ALTER COLUMN citizenship_no_norm DROP NOT NULL;

-- ---------------------------------------------------------------------
-- pg_trgm — trigram similarity, used below for fuzzy-name duplicate
-- detection among newborns (who have no NID/citizenship hash to dedup
-- against yet).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- vital_event — shared base table / state machine for all five event
-- types (§4.1). See VitalEvent.java's Javadoc for why this is a mutable
-- workflow row (like citizen_edit_requests) rather than an append-only
-- table: the design note for "UPDATE/DELETE revoked" fits citizen_events'
-- own discipline, not a row that has to move through a state machine
-- under the same case record. Tamper-evident history for every
-- transition instead lives in citizen_events (already hash-chained,
-- V31), which VitalEventService writes to on every transition.
CREATE TABLE vital_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type          VARCHAR(20)  NOT NULL
        CHECK (event_type IN ('BIRTH','DEATH','MARRIAGE','DIVORCE','MIGRATION')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED'
        CHECK (status IN ('SUBMITTED','PENDING_APPROVAL','APPROVED','REJECTED','CAO_REVIEW')),
    ward_id             UUID         NOT NULL REFERENCES ward(id),
    submitted_by        UUID         NOT NULL REFERENCES users(id),
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_by         UUID         REFERENCES users(id),
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    TEXT,
    -- Governance Tiers §6: set only by the auto-escalation job when a
    -- PENDING_APPROVAL event goes 5 business days unactioned. NULL means
    -- either not yet escalated, or escalated deliberately by a human.
    auto_escalated_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_vital_event_ward   ON vital_event(ward_id);
CREATE INDEX idx_vital_event_status ON vital_event(status);
CREATE INDEX idx_vital_event_type   ON vital_event(event_type);
-- Auto-escalation job's query shape: "PENDING_APPROVAL rows submitted
-- before <cutoff>".
CREATE INDEX idx_vital_event_status_submitted ON vital_event(status, submitted_at);

-- ---------------------------------------------------------------------
-- birth_record (§4.2) — 1:1 detail table for event_type = 'BIRTH'.
-- Father/mother link directly to an existing citizen when they're
-- already registered; free-text fallback columns cover the (very common
-- for a first-generation registration) case where a parent isn't in the
-- registry yet. child_citizen_id is populated only once the event is
-- APPROVED and a new citizen row is created for the newborn — NULL
-- while the submission is still pending.
CREATE TABLE birth_record (
    vital_event_id       UUID PRIMARY KEY REFERENCES vital_event(id) ON DELETE CASCADE,

    child_name_np         VARCHAR(300) NOT NULL,
    child_name_en         VARCHAR(300) NOT NULL,
    sex                   VARCHAR(10)  NOT NULL CHECK (sex IN ('MALE','FEMALE','OTHER')),
    date_of_birth         DATE         NOT NULL,
    place_of_birth        VARCHAR(300) NOT NULL,

    father_citizen_id     UUID REFERENCES citizen(id),
    father_name_text      VARCHAR(300),
    mother_citizen_id     UUID REFERENCES citizen(id),
    mother_name_text      VARCHAR(300),

    -- At least one parent must be identified one way or the other —
    -- either an existing citizen record or, failing that, a name on file.
    CONSTRAINT chk_birth_record_father_identified
        CHECK (father_citizen_id IS NOT NULL OR father_name_text IS NOT NULL),
    CONSTRAINT chk_birth_record_mother_identified
        CHECK (mother_citizen_id IS NOT NULL OR mother_name_text IS NOT NULL),

    birth_weight_kg       NUMERIC(4,2),
    delivery_type         VARCHAR(20) CHECK (delivery_type IN ('NORMAL','CESAREAN','ASSISTED') OR delivery_type IS NULL),
    attending_facility    VARCHAR(300),

    -- Set on APPROVED — the resulting citizen record for the newborn.
    child_citizen_id      UUID REFERENCES citizen(id),

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_birth_record_father ON birth_record(father_citizen_id);
CREATE INDEX idx_birth_record_mother ON birth_record(mother_citizen_id);
CREATE INDEX idx_birth_record_child  ON birth_record(child_citizen_id);

-- Fuzzy-name duplicate detection: a newborn has no NID/citizenship hash
-- to dedup against, so the only signal against double-registering the
-- same birth is similarity of (child name, DOB, ward) — trigram GIN
-- index makes "% similar name" queries fast instead of a sequential scan.
CREATE INDEX idx_birth_record_child_name_trgm
    ON birth_record USING gin (child_name_en gin_trgm_ops);
