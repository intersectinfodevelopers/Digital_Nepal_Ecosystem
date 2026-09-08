-- V41__relying_party_gateway.sql
--
-- Phase 3 (Extended Modules §6.1-6.5): External Relying-Party Gateway.
-- Lets a licensed outside organization verify one fact about a citizen —
-- with the citizen's consent, purpose-scoped to only the fields that
-- purpose needs — without ever holding the citizen's actual record.
--
-- SCOPE NOTE: mutual TLS is stored as data here (client_certificate_
-- fingerprint, certificate_expires_at) and checked at the application
-- level, not enforced at the TLS handshake itself. Real mTLS needs an
-- actual certificate authority and a real relying party on the other
-- end of the connection to test against — neither exists yet, and which
-- one(s) do is itself downstream of the "Data residency" decision the
-- plan's own Concept Document §15 lists as gating this phase (same
-- reasoning already applied to the payment-rail and evidence-agency
-- callbacks). What's real here: the licensing, the purpose-scoped field
-- whitelist, the pairwise tokens, the OTP consent flow, and the access
-- log — none of that needs a live counterparty to be genuine.
CREATE TABLE relying_party (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name                            VARCHAR(300) NOT NULL,
    organization_type               VARCHAR(20) NOT NULL CHECK (organization_type IN ('GOVERNMENT', 'COMMERCIAL', 'NGO')),

    -- OAuth2 client-credentials identity. client_secret_hash is a bcrypt
    -- hash — the plaintext secret is shown to the licensing Central Admin
    -- exactly once, at creation, and never stored or retrievable again
    -- (same one-way discipline as a user's own password_hash).
    client_id                       VARCHAR(100) NOT NULL UNIQUE,
    client_secret_hash              TEXT NOT NULL,

    client_certificate_fingerprint  VARCHAR(200),
    certificate_expires_at          TIMESTAMPTZ,

    status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    suspension_reason               TEXT,

    -- Real-time anomaly counter (Extended Modules §6.5's
    -- GatewayAnomalyMonitorJob) — incremented on every DENIED verify
    -- call, reset on any SUCCESS, checked inline by
    -- GatewayVerificationService rather than a periodic scan. See that
    -- service's Javadoc for why: immediate response to a live abuse
    -- pattern beats a job that might not run again for up to an hour.
    consecutive_denied_count        INTEGER NOT NULL DEFAULT 0,

    licensed_by                     UUID NOT NULL REFERENCES users(id),
    licensed_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_relying_party_status ON relying_party(status);
-- RelyingPartyCertExpiryJob's query shape.
CREATE INDEX idx_relying_party_cert_expiry ON relying_party(status, certificate_expires_at);

-- relying_party_purpose (§6.2) — purpose-scoped field whitelisting. A
-- relying party can only ever request exactly the fields listed here for
-- a given purpose_code, never a citizen's full record.
CREATE TABLE relying_party_purpose (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    relying_party_id           UUID NOT NULL REFERENCES relying_party(id),
    purpose_code                VARCHAR(50) NOT NULL,
    allowed_fields              JSONB NOT NULL,

    -- False only for a narrow legal-exception path
    -- (LEGAL_MANDATE_NO_CONSENT) — requires dual sign-off (two distinct
    -- Central Admins) below before it can actually be set false; a
    -- freshly created purpose always starts at TRUE (requiring citizen
    -- OTP consent) regardless of organization_type.
    requires_consent            BOOLEAN NOT NULL DEFAULT TRUE,
    no_consent_approved_by_1    UUID REFERENCES users(id),
    no_consent_approved_by_2    UUID REFERENCES users(id),
    CONSTRAINT chk_no_consent_dual_signoff_distinct
        CHECK (no_consent_approved_by_1 IS NULL OR no_consent_approved_by_2 IS NULL
               OR no_consent_approved_by_1 <> no_consent_approved_by_2),

    -- Graduated penalty ladder's first rung (§6.5) — suspend the
    -- specific purpose before suspending the whole relying party.
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (relying_party_id, purpose_code)
);

CREATE INDEX idx_relying_party_purpose_party ON relying_party_purpose(relying_party_id);

-- citizen_relying_party_token (§6.3) — Aadhaar-style pairwise reference
-- tokens. Unique per (citizen, relying party) so two different relying
-- parties can never correlate the same citizen via a shared identifier —
-- each only ever sees its own token for that person.
CREATE TABLE citizen_relying_party_token (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id          UUID NOT NULL REFERENCES citizen(id),
    relying_party_id   UUID NOT NULL REFERENCES relying_party(id),
    token                UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (citizen_id, relying_party_id),
    UNIQUE (token)
);

-- gateway_consent (§6.4) — OTP-based commercial consent, via
-- SparrowSmsService (already built for ID-card notifications). otp_
-- code_hash, never plaintext — same one-way discipline as a password.
CREATE TABLE gateway_consent (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id          UUID NOT NULL REFERENCES citizen(id),
    relying_party_id   UUID NOT NULL REFERENCES relying_party(id),
    purpose_code        VARCHAR(50) NOT NULL,

    otp_code_hash       TEXT NOT NULL,
    otp_expires_at      TIMESTAMPTZ NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED')),
    confirmed_at        TIMESTAMPTZ,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gateway_consent_lookup ON gateway_consent(citizen_id, relying_party_id, purpose_code, status);

-- gateway_access_log (§6.5's transparency requirement, and the citizen-
-- facing GET /citizens/{id}/access-log) — every verify() call, whatever
-- its outcome, so a citizen (or an admin on their behalf — there is no
-- citizen self-service portal in this backend yet) can see exactly who
-- looked at what, when, and why.
CREATE TABLE gateway_access_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id          UUID NOT NULL REFERENCES citizen(id),
    relying_party_id   UUID NOT NULL REFERENCES relying_party(id),
    purpose_code        VARCHAR(50) NOT NULL,
    fields_disclosed    JSONB,
    consent_method      VARCHAR(30) CHECK (consent_method IN ('OTP', 'LEGAL_MANDATE_NO_CONSENT') OR consent_method IS NULL),
    outcome              VARCHAR(20) NOT NULL
        CHECK (outcome IN ('SUCCESS', 'DENIED_SCOPE', 'DENIED_CONSENT', 'DENIED_SUSPENDED')),
    accessed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gateway_access_log_citizen ON gateway_access_log(citizen_id, accessed_at);
CREATE INDEX idx_gateway_access_log_party   ON gateway_access_log(relying_party_id, accessed_at);
