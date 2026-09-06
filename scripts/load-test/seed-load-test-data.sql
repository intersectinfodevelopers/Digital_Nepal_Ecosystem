-- ============================================================================
-- Load-test fixtures — Kummayak Rural Municipality (9-ward pilot)
--
-- WHY THIS FILE EXISTS
-- V1__initial_schema.sql only seeds Ward 1 and Ward 2 ("Full seed script
-- (all 9 wards + admin accounts) will be added by Amit in V2" — never
-- happened; no later migration adds wards 3-9 or any users at all). There
-- is also no self-service user-creation endpoint (AuthController only
-- exposes login/refresh/logout/me; ApprovalController assumes an
-- authenticated admin already exists). Without this file there is no way
-- to obtain a WARD_ADMIN JWT to call POST /v1/citizens/register at all.
--
-- This is NOT a Flyway migration and must NOT be added to db/migrations/.
-- These are throwaway test accounts with a shared, published password —
-- shipping them via Flyway would mean they exist in every environment
-- Flyway runs against, including staging/prod. Apply by hand, only
-- against a load-test/dev database, using load-and-run.sh below.
--
-- Login credentials for every seeded admin below:
--   password: LoadTest!2026
--   (bcrypt, cost 10 — matches SecurityConfig's BCryptPasswordEncoder default)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Wards 3-9 (Ward 1 & 2 already exist from V1__initial_schema.sql)
-- All under Kummayak Rural Municipality (b2c3d4e5-f6a7-8901-bcde-f12345678901)
-- ----------------------------------------------------------------------------
INSERT INTO ward (id, municipality_id, ward_no, name_np, name_en) VALUES
                                                                      ('9cce9e6b-6cca-5fd1-8f1e-e4b6e90e6a04', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 3, 'वडा नं. ३', 'Ward No. 3'),
                                                                      ('e64f2041-d926-5a1c-8f3f-a2e182cf4f8c', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 4, 'वडा नं. ४', 'Ward No. 4'),
                                                                      ('ea1ec0ab-adb1-5ee8-80ac-5ea0d59dd678', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 5, 'वडा नं. ५', 'Ward No. 5'),
                                                                      ('87f18d6b-17b1-5f7c-95a3-6a97f8c48a96', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 6, 'वडा नं. ६', 'Ward No. 6'),
                                                                      ('6f134ea2-622f-5ba4-8f42-7c6c9587bfeb', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 7, 'वडा नं. ७', 'Ward No. 7'),
                                                                      ('c13e69a3-4cb2-5446-986f-45f100c2527b', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 8, 'वडा नं. ८', 'Ward No. 8'),
                                                                      ('d04fcc6e-3fa6-5b9c-ad28-b5315ead1ef6', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 9, 'वडा नं. ९', 'Ward No. 9')
    ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- One WARD_ADMIN per ward (1-9). Satisfies both the legacy V1 NOT NULL
-- columns (username, password_hash, jurisdiction_type, jurisdiction_id) and
-- the V19 entity-mapped columns (email, password, ward_id) that AuthService
-- actually reads from, plus V19's chk_users_single_jurisdiction constraint.
-- ----------------------------------------------------------------------------
INSERT INTO users (
    id, username, password_hash, role, jurisdiction_type, jurisdiction_id,
    full_name, email, password, ward_id, enabled, account_non_locked,
    failed_attempts, password_reset_required
) VALUES
      ('f72e59e3-b24a-59bd-ae11-e90c29c64aa9', 'loadtest.ward1.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', 'c3d4e5f6-a7b8-9012-cdef-123456789012', 'Load Test Ward 1 Admin', 'ward1.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'c3d4e5f6-a7b8-9012-cdef-123456789012', TRUE, TRUE, 0, FALSE),
      ('9a5ca351-769a-553e-9a18-7cc5f8895852', 'loadtest.ward2.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', 'd4e5f6a7-b8c9-0123-def0-234567890123', 'Load Test Ward 2 Admin', 'ward2.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'd4e5f6a7-b8c9-0123-def0-234567890123', TRUE, TRUE, 0, FALSE),
      ('46732a1b-e7b5-51c9-9e93-af16e823d27d', 'loadtest.ward3.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', '9cce9e6b-6cca-5fd1-8f1e-e4b6e90e6a04', 'Load Test Ward 3 Admin', 'ward3.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', '9cce9e6b-6cca-5fd1-8f1e-e4b6e90e6a04', TRUE, TRUE, 0, FALSE),
      ('5ca16ed3-88d2-5965-b218-acdffb498a6e', 'loadtest.ward4.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', 'e64f2041-d926-5a1c-8f3f-a2e182cf4f8c', 'Load Test Ward 4 Admin', 'ward4.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'e64f2041-d926-5a1c-8f3f-a2e182cf4f8c', TRUE, TRUE, 0, FALSE),
      ('820c6048-0440-5743-819b-49096df5af8f', 'loadtest.ward5.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', 'ea1ec0ab-adb1-5ee8-80ac-5ea0d59dd678', 'Load Test Ward 5 Admin', 'ward5.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'ea1ec0ab-adb1-5ee8-80ac-5ea0d59dd678', TRUE, TRUE, 0, FALSE),
      ('62e1a2a2-86fa-5e57-8f70-0684d2172c8f', 'loadtest.ward6.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', '87f18d6b-17b1-5f7c-95a3-6a97f8c48a96', 'Load Test Ward 6 Admin', 'ward6.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', '87f18d6b-17b1-5f7c-95a3-6a97f8c48a96', TRUE, TRUE, 0, FALSE),
      ('4e8870b7-6823-598b-8025-f6f3c9fc8405', 'loadtest.ward7.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', '6f134ea2-622f-5ba4-8f42-7c6c9587bfeb', 'Load Test Ward 7 Admin', 'ward7.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', '6f134ea2-622f-5ba4-8f42-7c6c9587bfeb', TRUE, TRUE, 0, FALSE),
      ('c6868033-5b7e-55cc-95eb-e05ebca30e95', 'loadtest.ward8.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', 'c13e69a3-4cb2-5446-986f-45f100c2527b', 'Load Test Ward 8 Admin', 'ward8.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'c13e69a3-4cb2-5446-986f-45f100c2527b', TRUE, TRUE, 0, FALSE),
      ('04580a83-0b9e-5b48-a873-daa7e86e4811', 'loadtest.ward9.admin', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'WARD_ADMIN', 'WARD', 'd04fcc6e-3fa6-5b9c-ad28-b5315ead1ef6', 'Load Test Ward 9 Admin', 'ward9.admin@loadtest.kummayak.gov.np', '$2b$10$LEC35UrmfFgGHXmQcGopeOZ.hAgUVg/dzk1lZ21W19vMYOYt6cUmW', 'd04fcc6e-3fa6-5b9c-ad28-b5315ead1ef6', TRUE, TRUE, 0, FALSE)
    ON CONFLICT (id) DO NOTHING;