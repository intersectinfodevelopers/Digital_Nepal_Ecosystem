-- V33__household_living_standard_extension.sql
--
-- Household Living-Standard Extension, Day 2 of 2 (Housing, Land & Utilities
-- fields only — Days 2-3 split per the 15-Day Work Plan). Adds the first 7 of
-- 25 new `household` columns specified in Technical System Design — Extended
-- Modules §8 ("Household Schema Extension (Living Standard)"). The remaining
-- 18 columns (assets, access, food security, social security, distance
-- bands) are added in a follow-up migration on Day 3.
--
-- Renumbered from the plan's suggested V42 to V33: this repo's highest merged
-- migration at the time of writing is V32
-- (V32__widen_refresh_token_column.sql), so V33 is the actual next-available
-- number. Re-confirmed against `develop` immediately before creating this
-- file.
--
-- This is purely additive. Per Extended Modules §1's stated convention
-- ("This document is additive... only referenced, not redefined, unless
-- explicitly marked [MODIFIED]") and confirmed by §8's own DDL (ADD COLUMN
-- only, no RENAME/DROP), none of the existing V10 household columns are
-- touched -- including `electricity`, `water_source`, `sanitation`, and
-- `internet_access`, which look similar to some of the new columns below but
-- are left exactly as they are. See docs/household-living-standard-notes.md
-- for the full field-by-field source trail.
--
-- CHECK constraints and VARCHAR lengths below are copied verbatim from §8
-- Listing 8.1 -- no allowed-value list here was invented. §8 gives no CHECK
-- constraint for `owns_agricultural_land` (a plain BOOLEAN) even though the
-- plan's own Day 2 text says "each with an explicit CHECK constraint
-- matching Diagram H.1" -- the source document doesn't support one, so none
-- is added.
--
-- `ADD COLUMN IF NOT EXISTS` is used on every column, matching this repo's
-- established idempotent-migration convention (see V10, V14, V16, V19) --
-- §8's own listing doesn't include IF NOT EXISTS since it's a design spec,
-- not a repo-specific migration file.
--
-- No DEFAULT or NOT NULL is set on any column below, matching §8 exactly:
-- Listing 8.1 specifies neither for any of these 7 fields.

ALTER TABLE household
    ADD COLUMN IF NOT EXISTS house_construction_type VARCHAR(20)
    CHECK (house_construction_type IN
    ('RCC_PILLAR', 'CEMENT_BONDED', 'MUD_BONDED', 'WOOD', 'BAMBOO_THATCH', 'OTHER'));

ALTER TABLE household
    ADD COLUMN IF NOT EXISTS owns_agricultural_land BOOLEAN;

ALTER TABLE household
    ADD COLUMN IF NOT EXISTS land_holding_band VARCHAR(25)
    CHECK (land_holding_band IN
    ('BELOW_1_ROPANI', 'ROPANI_1_5', 'ROPANI_5_10', 'ROPANI_10_1BIGHA', 'BIGHA_1_5', 'ABOVE_5_BIGHA'));

ALTER TABLE household
    ADD COLUMN IF NOT EXISTS electricity_source VARCHAR(20)
    CHECK (electricity_source IN
    ('NATIONAL_GRID', 'SOLAR', 'MICRO_HYDRO', 'GENERATOR', 'NONE'));

ALTER TABLE household
    ADD COLUMN IF NOT EXISTS drinking_water_source VARCHAR(25)
    CHECK (drinking_water_source IN
    ('PIPED_INSIDE', 'PIPED_YARD', 'PUBLIC_TAP', 'TUBE_WELL', 'COVERED_WELL',
    'UNCOVERED_WELL', 'RIVER_STREAM', 'JAR_TANKER', 'OTHER'));

ALTER TABLE household
    ADD COLUMN IF NOT EXISTS toilet_facility VARCHAR(20)
    CHECK (toilet_facility IN
    ('FLUSH_CONNECTED', 'FLUSH_SEPTIC', 'PIT_LATRINE', 'SHARED', 'NONE'));

ALTER TABLE household
    ADD COLUMN IF NOT EXISTS cooking_fuel VARCHAR(20)
    CHECK (cooking_fuel IN
    ('LPG', 'FIREWOOD', 'BIOGAS', 'ELECTRICITY', 'KEROSENE', 'AGRI_RESIDUE', 'OTHER'));