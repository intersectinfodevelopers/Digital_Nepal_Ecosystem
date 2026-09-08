-- V40__evidence_request.sql
--
-- Phase 3 (Governance Tiers §7): Once-Only Evidence Exchange. One logged,
-- purpose-specific request per fact needed from an external authoritative
-- source (DAO for citizenship, NIDMC for National ID, the Election
-- Commission for voter-roll facts) — never a bulk export. The "once-only"
-- principle: a citizen already verified by one government body shouldn't
-- have to re-prove the same fact to another; this system requests
-- confirmation of one specific fact instead.
CREATE TABLE evidence_request (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    citizen_id         UUID NOT NULL REFERENCES citizen(id),
    target_agency      VARCHAR(30) NOT NULL CHECK (target_agency IN ('DAO', 'NIDMC', 'ELECTION_COMMISSION')),

    -- What this request is FOR (why we need the fact) and what fact,
    -- specifically, is being asked — kept as two separate free-text
    -- fields rather than one, since "purpose" drives access-control
    -- decisions in a way "fact requested" (arbitrary agency-specific
    -- wording) shouldn't need to.
    purpose            VARCHAR(50) NOT NULL,
    fact_requested     VARCHAR(300) NOT NULL,

    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RESPONDED', 'FAILED', 'EXPIRED')),

    -- The external agency's answer, once received — a small structured
    -- fact (e.g. {"citizenship_valid": true}), not a document or a
    -- citizen record. Kept as JSONB rather than a fixed column per fact
    -- type since different agencies answer different questions.
    response_payload   JSONB,
    failure_reason     TEXT,

    requested_by       UUID NOT NULL REFERENCES users(id),
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at       TIMESTAMPTZ,

    -- A request that sits PENDING past this should be treated as
    -- EXPIRED, not left open indefinitely — see
    -- EvidenceRequestExpiryJob. 30 days matches a typical government
    -- correspondence SLA; not sourced from a specific inter-agency
    -- agreement (none exists yet for this system to reference).
    expires_at         TIMESTAMPTZ NOT NULL,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_evidence_request_citizen  ON evidence_request(citizen_id);
CREATE INDEX idx_evidence_request_status   ON evidence_request(status);
CREATE INDEX idx_evidence_request_agency   ON evidence_request(target_agency);
-- EvidenceRequestExpiryJob's query shape: "PENDING requests past their
-- own expires_at".
CREATE INDEX idx_evidence_request_pending_expiry ON evidence_request(status, expires_at);
