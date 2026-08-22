-- V1__initial_schema.sql
-- Placeholder for initial schema creation: create core citizen tables, users, roles, basic reference data, and extensions (PostGIS).
-- Example actions:
--   - CREATE schema statements
--   - CREATE TABLE citizen (...)
--   - CREATE TABLE users, roles, permissions
--   - CREATE EXTENSION postgis;

/* Migration SQL goes here */

-- EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS postgis;      -- spatial queries (citizen_gis, Day 2+)

-- TABLE: province
-- Nepal's 7 provinces. Province Admins are scoped to one province at creation.
CREATE TABLE IF NOT EXISTS province (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_np         VARCHAR(200) NOT NULL,
    name_en         VARCHAR(200) NOT NULL,
    province_no     SMALLINT    NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- TABLE: municipality
-- Nepal's 753 local bodies. Local Body Admins are scoped to one municipality.
-- Write authority over citizen records lives at this tier and below.
CREATE TABLE IF NOT EXISTS municipality (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    province_id         UUID NOT NULL REFERENCES province(id),
    name_np             VARCHAR(200) NOT NULL,
    name_en             VARCHAR(200) NOT NULL,
    -- RURAL_MUNICIPALITY / MUNICIPALITY / SUB_METROPOLITAN / METROPOLITAN
    municipality_type   VARCHAR(50)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_municipality_province ON municipality(province_id);

-- TABLE: ward
-- Nepal's 6,743 wards. Ward Admins enter citizen data scoped to one ward.
-- This is the RLS enforcement boundary for all citizen queries.
CREATE TABLE IF NOT EXISTS ward (
    id                  UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    municipality_id     UUID     NOT NULL REFERENCES municipality(id),
    ward_no             INTEGER NOT NULL,
    name_np             VARCHAR(200) NOT NULL,
    name_en             VARCHAR(200) NOT NULL,
    population_estimate INTEGER,
    area_sq_km          DECIMAL(8,3),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (municipality_id, ward_no)
);
CREATE INDEX idx_ward_municipality ON ward(municipality_id);

-- TABLE: users
-- All 4 government tiers: CENTRAL / PROVINCE / LOCAL_BODY / WARD
-- jurisdiction_id references the appropriate tier table (province/municipality/ward).
-- Account is bound to exactly one geographic scope at creation — immutable.
CREATE TABLE IF NOT EXISTS users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username                VARCHAR(100) NOT NULL UNIQUE,
    -- BCrypt strength 12 — enforced in UserService
    password_hash           TEXT         NOT NULL,
    -- CENTRAL_ADMIN / PROVINCE_ADMIN / LOCAL_BODY_ADMIN / WARD_ADMIN
    role                    VARCHAR(30)  NOT NULL,
    -- NATIONAL / PROVINCE / MUNICIPALITY / WARD
    jurisdiction_type       VARCHAR(30)  NOT NULL,
    -- UUID referencing province.id / municipality.id / ward.id depending on role
    jurisdiction_id         UUID         NOT NULL,
    full_name               VARCHAR(300) NOT NULL,
    -- AES-256 encrypted contact fields (populated by Dharmapal's auth module)
    phone_enc               TEXT,
    email_enc               TEXT,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login              TIMESTAMPTZ,
    failed_logins           SMALLINT     NOT NULL DEFAULT 0,
    -- Account locked until this timestamp after too many failed logins
    locked_until            TIMESTAMPTZ,
    password_changed_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_jurisdiction ON users(jurisdiction_id);

-- TABLE: citizen
-- Core identity record for every registered citizen.
--
-- ENCRYPTION NOTE:
-- All columns ending in _enc are encrypted at application layer (AES-256/GCM)
-- by NidEncryptionUtil BEFORE Hibernate persists them. This migration creates
-- the columns as TEXT — the encryption is NOT done at DB layer.
--
-- RLS NOTE:
-- ward_id is the RLS boundary column. Policies added in V4 (Sachin, Day 6)
-- filter every SELECT to app.current_ward_id / app.current_municipality_id /
-- app.current_province_id set from JWT claims by GeographicScopeFilter.
CREATE TABLE IF NOT EXISTS citizen (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- RLS boundary — all queries filtered by this column
    ward_id                 UUID NOT NULL REFERENCES ward(id),

    -- IDENTITY — PII encrypted at application layer
    nid_enc                 TEXT,           -- AES-256 encrypted National ID
    -- SHA-256 hash of plaintext NID for uniqueness checks without decrypting.
    -- Day 2: upgraded to HMAC-SHA256 with application pepper (nid_hmac).
    nid_hash                VARCHAR(64)     NOT NULL,
    citizenship_no_enc      TEXT,           -- AES-256 encrypted citizenship number
    -- Alphanumeric-sanitized citizenship number (dashes/slashes stripped).
    -- Used for family tree auto-linking across district formatting differences.
    citizenship_no_norm     VARCHAR(100)    NOT NULL,
    passport_no_enc         TEXT,           -- nullable — not all citizens have passports

    -- NAME — NOT encrypted (displayed on ID cards and dashboards)
    name_np                 VARCHAR(300)    NOT NULL,   -- Devanagari
    name_en                 VARCHAR(300)    NOT NULL,

    -- DEMOGRAPHICS
    dob_enc                 TEXT            NOT NULL,   -- AES-256 encrypted date of birth
    sex                     VARCHAR(10)     NOT NULL,   -- MALE / FEMALE / OTHER
    blood_group             VARCHAR(5),
    religion                VARCHAR(100),
    ethnicity               VARCHAR(100),
    mother_tongue           VARCHAR(100),
    tole                    VARCHAR(200),               -- sub-ward settlement name

    -- CONTACT — encrypted
    phone_enc               TEXT,
    phone_alt_enc           TEXT,
    email_enc               TEXT,

    -- DIGITAL PROFILE
    digital_literacy        VARCHAR(20),   -- NONE / BASIC / INTERMEDIATE / ADVANCED
    has_smartphone          BOOLEAN        DEFAULT FALSE,
    photo_url               TEXT,

    -- NID VERIFICATION
    -- True if NID was verified synchronously (online registration).
    nid_verified            BOOLEAN        DEFAULT FALSE,
    -- True after async NID batch job clears this record post-sync.
    -- Benefit eligibility + ID card issuance BLOCKED while false.
    is_async_verified       BOOLEAN        DEFAULT FALSE,
    nid_verified_at         TIMESTAMPTZ,

    -- CONSENT — Individual Privacy Act 2018 + Constitution Article 28
    consent_recorded_at     TIMESTAMPTZ    NOT NULL,
    consent_channel         VARCHAR(50)    NOT NULL,   -- WARD_OFFICE / FIELD / PORTAL

    -- OFFLINE SYNC TRACKING (Flutter mobile app)
    sync_status             VARCHAR(20)    DEFAULT 'SYNCED',  -- PENDING/SYNCED/CONFLICT/FAILED
    -- UUID generated by Flutter app BEFORE server assignment
    local_record_id         UUID,
    device_id               VARCHAR(200),
    last_synced_at          TIMESTAMPTZ,
    registration_channel    VARCHAR(50),   -- WARD_OFFICE / FIELD / PORTAL / MOBILE

    -- APPROVAL / ARCHIVE STATUS
    -- NULL = active | ARCHIVE_PENDING = awaiting dual approval | ARCHIVED = soft deleted
    archive_status          VARCHAR(30),

    -- OPTIMISTIC LOCKING — offline conflict detection
    -- Incremented on every approved edit.
    -- Spring Batch compares incoming device version → mismatch → conflict queue.
    version_number          INTEGER        NOT NULL DEFAULT 1,

    -- SOFT DELETE
    is_active               BOOLEAN        NOT NULL DEFAULT TRUE,
    archived_at             TIMESTAMPTZ,
    archived_by             UUID           REFERENCES users(id),

    -- AUDIT
    created_by              UUID           NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- INDEXES — citizen table

-- Primary lookup index — ward scoping for all dashboard queries
CREATE INDEX idx_citizen_ward ON citizen(ward_id);

-- Composite index for ward-scoped analytical aggregates
CREATE INDEX idx_citizen_active_ward ON citizen(ward_id, is_active);

-- NID hash lookup — used for duplicate detection during registration
CREATE INDEX idx_citizen_nid_hash ON citizen(nid_hash);

-- Citizenship number lookup — used for family tree auto-linking
CREATE INDEX idx_citizen_cit_norm ON citizen(citizenship_no_norm);

-- Sync status index — used by Spring Batch sync job reader
CREATE INDEX idx_citizen_sync_status ON citizen(sync_status) WHERE sync_status != 'SYNCED';

-- UNIQUE CONSTRAINTS — partial indexes (active records only)
-- Allows re-registration after a record is archived (is_active = false).

-- Enforce NID uniqueness only among active citizens
CREATE UNIQUE INDEX uq_active_citizen_nid
    ON citizen(nid_hash)
    WHERE (is_active = TRUE);

-- Enforce citizenship number uniqueness only among active citizens
CREATE UNIQUE INDEX uq_active_citizen_cit_norm
    ON citizen(citizenship_no_norm)
    WHERE (is_active = TRUE);

-- SEED DATA — Kummayak Rural Municipality
-- Minimal seed so the application boots and admins can log in on Day 1.
-- Full seed script (all 9 wards + admin accounts) will be added by Amit in V2.

-- Koshi Province (Province No. 1)
INSERT INTO province (id, name_np, name_en, province_no)
VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'कोशी प्रदेश',
    'Koshi Province',
    1
) ON CONFLICT DO NOTHING;

-- Kummayak Rural Municipality
INSERT INTO municipality (id, province_id, name_np, name_en, municipality_type)
VALUES (
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'कुम्मायक गाउँपालिका',
    'Kummayak Rural Municipality',
    'RURAL_MUNICIPALITY'
) ON CONFLICT DO NOTHING;

-- Ward 1 (pilot ward for MVP go-live)
INSERT INTO ward (id, municipality_id, ward_no, name_np, name_en)
VALUES (
    'c3d4e5f6-a7b8-9012-cdef-123456789012',
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    1,
    'वडा नं. १',
    'Ward No. 1'
) ON CONFLICT DO NOTHING;

-- Ward 2
INSERT INTO ward (id, municipality_id, ward_no, name_np, name_en)
VALUES (
    'd4e5f6a7-b8c9-0123-def0-234567890123',
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    2,
    'वडा नं. २',
    'Ward No. 2'
) ON CONFLICT DO NOTHING;