-- V34__death_record_and_verbal_autopsy.sql
--
-- Phase 2 (SDD Extended Modules §4.3): death registration, the second
-- concrete vital event type built on the vital_event base table from V33.

CREATE TABLE death_record (
    vital_event_id            UUID PRIMARY KEY REFERENCES vital_event(id) ON DELETE CASCADE,

    -- The deceased MUST be an existing citizen — unlike birth, there is no
    -- "not registered yet" fallback for a death.
    citizen_id                UUID NOT NULL REFERENCES citizen(id),

    date_of_death             DATE         NOT NULL,
    place_of_death            VARCHAR(300) NOT NULL,

    -- Nullable — may still be pending a verbal autopsy interview (below)
    -- for a death outside a health facility.
    immediate_cause_of_death  VARCHAR(300),
    manner_of_death           VARCHAR(20)
        CHECK (manner_of_death IN ('NATURAL','ACCIDENT','SUICIDE','HOMICIDE','UNDETERMINED') OR manner_of_death IS NULL),

    informant_name            VARCHAR(300) NOT NULL,
    informant_relation        VARCHAR(100) NOT NULL,
    certifying_facility       VARCHAR(300),

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_death_record_citizen ON death_record(citizen_id);

-- ERR_DEATH_ALREADY_RECORDED (SDD Extended Modules §10): a citizen should
-- have at most one non-rejected death_record. NOT enforced as a DB
-- constraint here — Postgres partial-index predicates must be immutable
-- expressions over the indexed table's own columns and cannot reference
-- vital_event.status via a subquery, and death_record has no status
-- column of its own to build a simple partial index on (the status lives
-- on vital_event, one table away). Enforced instead in
-- DeathRegistrationService at two points: (1) at submission, rejecting a
-- second registration for a citizen with an existing non-rejected death
-- record; (2) again at approval time, re-checking citizen.status <>
-- DECEASED immediately before applying the cascade, closing the race
-- window between two concurrent submissions that both passed check (1).

-- verbal_autopsy_response (§4.3) — the WHO 2016 VA instrument, used when a
-- death occurs outside a health facility and has no other certified cause.
-- Optional, and can be attached before or after the death event itself is
-- approved/rejected — it doesn't participate in vital_event's state
-- machine. The full WHO instrument is ~100 structured questions across
-- several modules (adult/child/neonatal, each with its own branching
-- questions); modelled as JSONB rather than one column per question, which
-- would make this migration (and every future WHO instrument revision)
-- unmanageable. probable_cause_of_death is filled in later, once someone
-- (a physician or a physician-reviewed algorithm) codes the raw responses
-- into an ICD-10 cause — nullable until then.
CREATE TABLE verbal_autopsy_response (
    vital_event_id          UUID PRIMARY KEY REFERENCES death_record(vital_event_id) ON DELETE CASCADE,
    respondent_name         VARCHAR(300) NOT NULL,
    respondent_relation     VARCHAR(100) NOT NULL,
    interview_date          DATE         NOT NULL,
    responses               JSONB        NOT NULL,
    probable_cause_of_death VARCHAR(300),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
