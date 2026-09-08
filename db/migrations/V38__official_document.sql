-- V38__official_document.sql
--
-- Phase 2 (SDD Extended Modules §4.7): a single persisted document/
-- certificate table shared by ID cards and vital-event certificates.
--
-- REVISED SCOPE, discovered while implementing this: the plan's own
-- language ("id_card renamed/generalised to official_document") assumes
-- an existing id_card table platform-idcard's controller/PDF/QR services
-- depend on. There is no such table — V3__identity_tables.sql
-- (id_card / id_card_history) was left as an unwritten placeholder
-- comment from day one, and IdCardController's initiate/approve/verify
-- endpoints operate entirely on hardcoded placeholder strings with
-- explicit "TODO: save id_card record to DB" comments — there was never
-- anything to migrate data out of. What actually needed building is the
-- real persistence layer, generalised from the start to also cover
-- birth/death/marriage/divorce certificates, rather than building
-- id_card for real first and then renaming it.
CREATE TABLE official_document (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_type       VARCHAR(30) NOT NULL CHECK (document_type IN (
        'DISABILITY_CARD', 'UNEMPLOYMENT_CARD',
        'BIRTH_CERTIFICATE', 'DEATH_CERTIFICATE',
        'MARRIAGE_CERTIFICATE', 'DIVORCE_CERTIFICATE'
    )),

    -- Who the document is issued to/about. A marriage or divorce
    -- produces one official_document row PER SPOUSE (each citizen_id is
    -- one spouse) rather than a single two-party row — the same pattern
    -- real paper certificates already follow (each spouse holds their
    -- own copy), and it keeps this table's shape uniform: exactly one
    -- subject citizen per row, regardless of document type.
    citizen_id          UUID NOT NULL REFERENCES citizen(id),

    -- NULL for ID cards, which aren't event-driven. Set for every
    -- certificate — the vital event whose approval authorized it.
    vital_event_id      UUID REFERENCES vital_event(id),

    status              VARCHAR(20) NOT NULL DEFAULT 'PRINT_PENDING'
        CHECK (status IN ('PRINT_PENDING', 'ISSUED', 'REVOKED')),

    -- The QrCodeService-signed verification token, generated at issuance
    -- — NULL until then. See OfficialDocumentService's Javadoc for the
    -- lookup-by-(citizen,type,issued-date) limitation this inherits from
    -- QrCodeService's existing token format (unchanged from what
    -- IdCardController already used).
    qr_token            TEXT,

    issued_at           TIMESTAMPTZ,
    -- NULL for certificates (birth/death/marriage/divorce certificates
    -- don't expire); ID cards do — 3 years, matching the placeholder
    -- logic IdCardController's old TODO comment already assumed.
    expires_at          TIMESTAMPTZ,

    initiated_by        UUID NOT NULL REFERENCES users(id),
    approved_by         UUID REFERENCES users(id),

    revoked_at          TIMESTAMPTZ,
    revoked_by          UUID REFERENCES users(id),
    revocation_reason   TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_official_document_citizen     ON official_document(citizen_id);
CREATE INDEX idx_official_document_vital_event ON official_document(vital_event_id);
CREATE INDEX idx_official_document_status      ON official_document(status);
-- Lookup shape QrCodeService's token verification needs: find the
-- matching document by (citizen, type, issued date).
CREATE INDEX idx_official_document_verify_lookup
    ON official_document(citizen_id, document_type, issued_at);
