-- V29__identity_vault.sql
--
-- Phase 1 Citizen Core Extensions (Technical System Design — Extended
-- Modules §2.3): NID and citizenship ciphertext move out of the citizen
-- row into an isolated vault table. citizen keeps only a reference token.
--
-- Scoping note: §5.1's protection-matrix table also lists dob_enc/
-- phone_enc/email_enc as "relocated to identity_vault," but the vault's
-- own DDL (this section) restricts vault_type to NID/CITIZENSHIP only, and
-- no dob_ref/phone_ref/email_ref column is ever defined anywhere in the
-- source documents. Treating the DDL as authoritative: DOB/phone/email
-- stay exactly where they are today (dob_enc/phone_enc/email_enc directly
-- on citizen) — only NID and citizenship number move.
--
-- True DB-role isolation ("application role has INSERT-only, no SELECT on
-- this table") needs a second Postgres role/credential and a second
-- application datasource — an infrastructure change beyond a Flyway
-- migration's scope, tracked as a follow-up. What ships here is the
-- logical separation: IdentityVaultService is the only class in the
-- codebase that ever reads or writes ciphertext.

CREATE TABLE identity_vault (
    reference_token         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vault_type              VARCHAR(20) NOT NULL CHECK (vault_type IN ('NID', 'CITIZENSHIP')),
    ciphertext              TEXT NOT NULL,
    encryption_key_version  VARCHAR(10) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE citizen ADD COLUMN nid_ref UUID REFERENCES identity_vault(reference_token);
ALTER TABLE citizen ADD COLUMN citizenship_ref UUID REFERENCES identity_vault(reference_token);

-- Backfill: move every existing citizen's nid_enc/citizenship_no_enc
-- ciphertext into vault rows and populate the new reference columns.
-- Per-row correlation needs a procedural loop — a plain INSERT...SELECT
-- can't hand back which vault row belongs to which citizen.
DO $$
DECLARE
    rec RECORD;
    new_nid_ref UUID;
    new_cit_ref UUID;
BEGIN
    FOR rec IN SELECT id, nid_enc, citizenship_no_enc FROM citizen LOOP
        IF rec.nid_enc IS NOT NULL THEN
            INSERT INTO identity_vault (vault_type, ciphertext, encryption_key_version)
            VALUES ('NID', rec.nid_enc, 'v1')
            RETURNING reference_token INTO new_nid_ref;
            UPDATE citizen SET nid_ref = new_nid_ref WHERE id = rec.id;
        END IF;

        IF rec.citizenship_no_enc IS NOT NULL THEN
            INSERT INTO identity_vault (vault_type, ciphertext, encryption_key_version)
            VALUES ('CITIZENSHIP', rec.citizenship_no_enc, 'v1')
            RETURNING reference_token INTO new_cit_ref;
            UPDATE citizen SET citizenship_ref = new_cit_ref WHERE id = rec.id;
        END IF;
    END LOOP;
END $$;

ALTER TABLE citizen DROP COLUMN nid_enc;
ALTER TABLE citizen DROP COLUMN citizenship_no_enc;

COMMENT ON TABLE identity_vault IS
    'Isolated store for NID/citizenship ciphertext (Extended Modules §2.3). Only IdentityVaultService reads or writes this table.';
