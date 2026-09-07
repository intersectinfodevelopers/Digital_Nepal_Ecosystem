-- V35__marriage_record_and_marital_status_history.sql
--
-- Phase 2 (SDD Extended Modules §4.4): marriage registration — gender-
-- neutral, hard-blocked on underage and bigamy — plus marital_status_history
-- for the ward-transfer side of a marriage.

CREATE TABLE marriage_record (
    vital_event_id          UUID PRIMARY KEY REFERENCES vital_event(id) ON DELETE CASCADE,

    -- Deliberately "spouse1"/"spouse2", not "husband"/"wife" — SDD §4.4
    -- calls this gender-neutral by design.
    spouse1_citizen_id      UUID NOT NULL REFERENCES citizen(id),
    spouse2_citizen_id      UUID NOT NULL REFERENCES citizen(id),
    CONSTRAINT chk_marriage_record_distinct_spouses CHECK (spouse1_citizen_id <> spouse2_citizen_id),

    marriage_date           DATE         NOT NULL,
    marriage_place          VARCHAR(300) NOT NULL,
    witness1_name           VARCHAR(300),
    witness2_name           VARCHAR(300),

    -- Which spouse (if either) is relocating to the other's ward as part
    -- of this marriage — an explicit choice the couple states, never
    -- assumed from gender. NULL means neither relocates. Cross-
    -- municipality relocation is out of scope here — see
    -- MarriageRegistrationService's Javadoc for why — so this is only
    -- ever the other spouse's ward, within the same municipality.
    relocating_citizen_id   UUID REFERENCES citizen(id),
    CONSTRAINT chk_marriage_record_relocating_is_a_spouse
        CHECK (relocating_citizen_id IS NULL
               OR relocating_citizen_id IN (spouse1_citizen_id, spouse2_citizen_id)),

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_marriage_record_spouse1 ON marriage_record(spouse1_citizen_id);
CREATE INDEX idx_marriage_record_spouse2 ON marriage_record(spouse2_citizen_id);

-- ERR_MARRIAGE_ALREADY_MARRIED (bigamy) is enforced in
-- MarriageRegistrationService against citizen.marital_status, not a DB
-- constraint here — same reasoning as death's ERR_DEATH_ALREADY_RECORDED
-- (V34): the authoritative "is this citizen currently married" signal is
-- citizen.marital_status, one table away from marriage_record, and
-- Postgres partial-index predicates can't reference another table's
-- column via subquery.

-- marital_status_history (§4.4) — records every marital-status transition
-- for a citizen, and the ward transfer (if any) that came with it. One
-- row per citizen per vital_event that changed their status — a marriage
-- writes two rows (one per spouse), not one.
CREATE TABLE marital_status_history (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id               UUID NOT NULL REFERENCES citizen(id),
    vital_event_id           UUID NOT NULL REFERENCES vital_event(id),
    previous_marital_status  VARCHAR(20),
    new_marital_status       VARCHAR(20) NOT NULL,
    previous_ward_id         UUID REFERENCES ward(id),
    new_ward_id              UUID REFERENCES ward(id),
    effective_date           DATE NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_marital_status_history_citizen ON marital_status_history(citizen_id);
CREATE INDEX idx_marital_status_history_event   ON marital_status_history(vital_event_id);
