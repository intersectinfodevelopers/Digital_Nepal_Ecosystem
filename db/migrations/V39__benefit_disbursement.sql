-- V39__benefit_disbursement.sql
--
-- Phase 3 (Governance Tiers §8): Government-to-Person Payments.
--
-- REVISED SCOPE, same story as V38's official_document: the plan
-- describes this as "benefit_disbursement gains payment_rail... turns a
-- manually recorded line item into a real electronic disbursement" —
-- phrasing that assumes a benefit_disbursement table already exists to
-- add columns to. It doesn't. There is no benefit, entitlement, or
-- disbursement table or entity anywhere in this codebase — the only
-- related code is EligibilityService.evaluate(), which computes
-- DISABILITY/UNEMPLOYMENT eligibility on demand and returns it, without
-- persisting an actual entitlement or tracking any payment. This
-- migration builds the whole thing, not just the payment-rail columns.
--
-- benefit_type is scoped to DISABILITY/UNEMPLOYMENT — the only two
-- categories EligibilityService actually evaluates today (SENIOR/
-- SINGLE_WOMAN/FARMER are IdCardType's own unimplemented Phase 2
-- placeholders, same scope boundary official_document's DocumentType
-- already follows).
CREATE TABLE benefit_disbursement (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    citizen_id           UUID NOT NULL REFERENCES citizen(id),
    benefit_type         VARCHAR(20) NOT NULL CHECK (benefit_type IN ('DISABILITY', 'UNEMPLOYMENT')),

    -- Monthly cash-transfer period this disbursement covers, e.g.
    -- '2026-09'. A citizen should have at most one disbursement per
    -- (benefit_type, period) — enforced below.
    period               VARCHAR(7) NOT NULL,
    amount_npr           NUMERIC(10,2) NOT NULL CHECK (amount_npr > 0),

    payment_rail         VARCHAR(20) NOT NULL
        CHECK (payment_rail IN ('BANK_TRANSFER', 'ESEWA', 'KHALTI', 'CONNECTIPS', 'CASH')),
    payment_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING', 'INITIATED', 'SETTLED', 'FAILED', 'CANCELLED')),

    -- The payment rail's own transaction reference — populated by its
    -- settlement callback, not at initiation. NULL for CASH (there is no
    -- external rail to reference).
    external_reference   VARCHAR(200),
    failure_reason       TEXT,

    initiated_by         UUID NOT NULL REFERENCES users(id),
    initiated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at           TIMESTAMPTZ,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_benefit_disbursement_citizen ON benefit_disbursement(citizen_id);
CREATE INDEX idx_benefit_disbursement_status  ON benefit_disbursement(payment_status);

-- At most one disbursement per citizen/benefit/period that hasn't
-- failed or been cancelled — a real unique constraint here (unlike
-- Phase 2's application-level "already active" checks) because all the
-- columns it needs live on this same table.
CREATE UNIQUE INDEX uq_benefit_disbursement_active_period
    ON benefit_disbursement(citizen_id, benefit_type, period)
    WHERE payment_status NOT IN ('FAILED', 'CANCELLED');
