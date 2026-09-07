-- V36__divorce_record.sql
--
-- Phase 2 (SDD Extended Modules §4.5): divorce registration — requires a
-- court order, deliberately never touches residency (no ward transfer,
-- unlike marriage).

CREATE TABLE divorce_record (
    vital_event_id     UUID PRIMARY KEY REFERENCES vital_event(id) ON DELETE CASCADE,

    spouse1_citizen_id UUID NOT NULL REFERENCES citizen(id),
    spouse2_citizen_id UUID NOT NULL REFERENCES citizen(id),
    CONSTRAINT chk_divorce_record_distinct_spouses CHECK (spouse1_citizen_id <> spouse2_citizen_id),

    divorce_date       DATE         NOT NULL,

    -- ERR_DIVORCE_NO_COURT_ORDER (SDD Extended Modules §10): a divorce
    -- cannot be registered without a court order reference. There is no
    -- document-upload/storage system anywhere in this codebase yet (see
    -- DeathRegistrationService's Javadoc on why ID cards aren't touched
    -- either) — court_order_no is the order's own reference number, not
    -- an uploaded file. Attaching the actual order document is future
    -- work for whenever vital_event_document (§4.7) exists.
    court_name         VARCHAR(300) NOT NULL,
    court_order_no     VARCHAR(200) NOT NULL,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_divorce_record_spouse1 ON divorce_record(spouse1_citizen_id);
CREATE INDEX idx_divorce_record_spouse2 ON divorce_record(spouse2_citizen_id);

-- "Both spouses must currently be married to each other" is enforced in
-- DivorceRegistrationService against citizen.marital_status/spouse_citizen_id,
-- not a DB constraint here — same reasoning as marriage/death's
-- application-level checks (the authoritative signal lives on citizen,
-- one table away from divorce_record).
