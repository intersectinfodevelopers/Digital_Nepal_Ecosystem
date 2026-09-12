# Day 1 Notes — Household Living-Standard Extension

## Purpose

Reviewed Day 1 column specification, per the plan's Day 1 "Done when" criterion: *"A
complete, reviewed column list exists (in a notes file, not yet a migration) matching
Diagram H.1 exactly."* Rebuilt from scratch against the actual **Technical System
Design — Extended Modules** document (§8, "Household Schema Extension (Living
Standard)"), which is the authoritative field list per the Day 1 task text itself.
**This is not a migration.**

## Existing Household columns (V10 — `db/migrations/V10__employment_disability_household.sql`)

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK |
| ward_id | UUID | FK → ward(id), NOT NULL |
| head_citizen_id | UUID | FK → citizen(id) |
| house_type | VARCHAR(30) | |
| construction_type | VARCHAR(30) | |
| room_count | SMALLINT | no CHECK in V10 |
| land_owned | BOOLEAN | DEFAULT FALSE |
| land_area_ropani | DECIMAL(8,2) | |
| land_location | VARCHAR(300) | |
| electricity | VARCHAR(30) | |
| water_source | VARCHAR(30) | |
| sanitation | VARCHAR(30) | |
| internet_access | VARCHAR(30) | |
| has_bank_account | BOOLEAN | DEFAULT FALSE |
| bank_name | VARCHAR(200) | |
| monthly_income_band | VARCHAR(30) | |
| annual_income_band | VARCHAR(30) | |
| dependent_count | SMALLINT | no CHECK in V10 |
| poverty_class | VARCHAR(30) | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

**Confirmed directly from §8:** all 21 of these stay exactly as-is. §8 is marked
`[MODIFIED: household]` but its DDL contains only `ADD COLUMN` statements — no
`RENAME`, `DROP`, or type change to any existing column. `electricity_source`,
`drinking_water_source`, `toilet_facility`, `has_internet`/`internet_type` are new,
additional columns; they do not replace `electricity`, `water_source`, `sanitation`,
or `internet_access`.

## New columns required — Extended Modules §8, Listing 8.1 (authoritative — 25 total)

Diagram H.1 (the ERD in the 15-day plan) shows only 24 of these rows and omits
`has_road_access`. §8's DDL is the fuller and authoritative source (per the Day 1
task text: "Read Technical System Design — Extended Modules §8 for the full target
field list") — this note file follows §8 where the two disagree.

| # | Column | PostgreSQL Type | CHECK constraint / allowed values | Default | Nullable | Source |
|---|---|---|---|---|---|---|
| 1 | house_construction_type | VARCHAR(20) | RCC_PILLAR, CEMENT_BONDED, MUD_BONDED, WOOD, BAMBOO_THATCH, OTHER | Not specified | Nullable (no NOT NULL in §8) | §8 Listing 8.1 |
| 2 | owns_agricultural_land | BOOLEAN | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 3 | land_holding_band | VARCHAR(25) | BELOW_1_ROPANI, ROPANI_1_5, ROPANI_5_10, ROPANI_10_1BIGHA, BIGHA_1_5, ABOVE_5_BIGHA | Not specified | Nullable | §8 Listing 8.1 |
| 4 | electricity_source | VARCHAR(20) | NATIONAL_GRID, SOLAR, MICRO_HYDRO, GENERATOR, NONE | Not specified | Nullable | §8 Listing 8.1; column name also used in §7.1 Listing 7.1 sample query |
| 5 | drinking_water_source | VARCHAR(25) | PIPED_INSIDE, PIPED_YARD, PUBLIC_TAP, TUBE_WELL, COVERED_WELL, UNCOVERED_WELL, RIVER_STREAM, JAR_TANKER, OTHER | Not specified | Nullable | §8 Listing 8.1; column name also used in §7.1 Listing 7.1 sample query |
| 6 | toilet_facility | VARCHAR(20) | FLUSH_CONNECTED, FLUSH_SEPTIC, PIT_LATRINE, SHARED, NONE | Not specified | Nullable | §8 Listing 8.1 |
| 7 | cooking_fuel | VARCHAR(20) | LPG, FIREWOOD, BIOGAS, ELECTRICITY, KEROSENE, AGRI_RESIDUE, OTHER | Not specified | Nullable | §8 Listing 8.1 |
| 8 | has_internet | BOOLEAN | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 9 | internet_type | VARCHAR(15) | MOBILE_DATA, BROADBAND, BOTH, NONE | Not specified | Nullable | §8 Listing 8.1 |
| 10 | mobile_phone_count | SMALLINT | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 11 | two_wheeler_count | SMALLINT | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 12 | four_wheeler_count | SMALLINT | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 13 | bicycle_count | SMALLINT | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 14 | bank_account_count | SMALLINT | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 15 | keeps_livestock | BOOLEAN | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 16 | has_health_insurance | BOOLEAN | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 17 | receives_social_security | BOOLEAN | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 18 | social_security_type | VARCHAR(25) | SENIOR_CITIZEN, SINGLE_WOMAN, DISABILITY, CHILD_NUTRITION, ENDANGERED_ETHNICITY, DALIT_SENIOR, OTHER | Not specified | Nullable | §8 Listing 8.1 |
| 19 | has_migrant_member_12mo | BOOLEAN | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 20 | has_road_access | BOOLEAN | None specified | Not specified | Nullable | §8 Listing 8.1 (absent from Diagram H.1's ERD — see note above) |
| 21 | road_distance_band | VARCHAR(15) | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 22 | market_distance_band | VARCHAR(15) | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 23 | health_facility_distance_band | VARCHAR(15) | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 24 | school_distance_band | VARCHAR(15) | None specified | Not specified | Nullable | §8 Listing 8.1 |
| 25 | food_sufficiency | VARCHAR(20) | YEAR_ROUND, MONTHS_9_12, MONTHS_6_9, MONTHS_3_6, UNDER_3_MONTHS, NOT_APPLICABLE | Not specified | Nullable | §8 Listing 8.1 |

**Confirmed from another design section:** `electricity_source` and
`drinking_water_source` (columns 4–5) are also referenced by exact name in §7.1's
sample mapping query (`h.electricity_source`, `h.drinking_water_source`), which is
independent confirmation these are the correct final column names, not a typo in §8.

**§8's own trailing comment on the distance-band fields (quoted for traceability,
not reworded, since it is design intent, not prose to paraphrase):**
> "*_distance_band fields are self-reported (interview); citizen_gis.dist_*_km fields
> (v1.0 Sec 4.4.1) remain the GPS-measured counterpart. Systematic divergence between
> the two, aggregated per ward, is a standing data-quality signal (Section 7.4
> warehouse)."

This confirms the four `*_distance_band` columns are deliberately separate from
`citizen_gis`'s existing `dist_health_post_km` / `dist_school_km` / `dist_market_km`
/ `dist_bank_km` columns (confirmed to exist in this repo's `V4` migration and
`CitizenGis.java`) — they are not meant to replace or reconcile with them in this
migration.

## Not specified / unresolved

1. **v1.0 Section 4.4.1** — cited by §8 as the source for `citizen_gis.dist_*_km`,
   but the v1.0 System Design Document itself is not part of the uploaded materials,
   so this specific cross-reference cannot be independently verified (the columns it
   points to do exist in the repo, which is consistent, but the section number itself
   is unconfirmed).
2. **Relationship between `owns_agricultural_land` (new) and `land_owned` (existing
   V10 column)** — §8 does not clarify whether these are meant to capture different
   things (agricultural land specifically vs. land ownership generally) or overlap.
   Not stated; worth a quick confirmation before Day 4's entity work, not a Day 1
   blocker.
3. **Repository migration numbering** — separate from the column list: this repo's
   highest migration is `V32`, so the next available number is `V33`, not the plan's
   assumed `V42`. Re-confirm against `develop` immediately before branching for Day 2.

## Second-pass field-by-field verification

Re-walked all 25 §8 rows against the previously-reviewed draft `ALTER TABLE
household` statement, character by character, including the trailing comment.

**Result: PASS.** The draft SQL matches §8 Listing 8.1 exactly — same 25 columns,
same names, same types/lengths, same CHECK lists, same absence of DEFAULT/NOT NULL,
same trailing comment verbatim. No discrepancies found between that SQL and the
authoritative source.

The only discrepancy in the entire research trail is external to that SQL: **Diagram
H.1's ERD is incomplete relative to §8** (missing `has_road_access`, and using an
abbreviated `dist_band` label instead of §8's full `distance_band` naming). Use §8,
not the diagram, as the source of truth going into Day 2.