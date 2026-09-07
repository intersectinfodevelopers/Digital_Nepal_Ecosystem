-- V28__citizenship_hmac_dedup.sql
--
-- Phase 1 Citizen Core Extensions (Technical System Design — Extended
-- Modules §2.2): citizenship certificate and National ID are legally
-- independent documents in Nepal — either must independently dedupe.
-- citizenship_hmac is the same HMAC-SHA256 + pepper construction already
-- used for nid_hmac (NidEncryptionUtil.hmac), applied to the citizenship
-- number instead. citizenship_no_norm (plaintext) stays scoped to the
-- family-link join only — never returned by any API, never used for
-- duplicate detection now that a non-brute-forceable HMAC exists.

ALTER TABLE citizen ADD COLUMN citizenship_hmac VARCHAR(64);

CREATE INDEX idx_citizen_citizenship_hmac ON citizen(citizenship_hmac);

CREATE UNIQUE INDEX uq_active_citizen_cit_hmac
    ON citizen(citizenship_hmac)
    WHERE (is_active = TRUE AND citizenship_hmac IS NOT NULL);

COMMENT ON COLUMN citizen.citizenship_hmac IS
    'HMAC-SHA256(citizenship_no, pepper) -- pepper stored only in Vault/env (app.pepper), never in this database. Independent dedup from nid_hmac: either document can block a duplicate registration on its own.';
