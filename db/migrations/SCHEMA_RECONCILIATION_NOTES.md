# Schema Reconciliation Notes

## Problem found (read-only audit, Aug 2026)

Three separate, mutually-conflicting definitions of the province/municipality/ward
and citizen tables existed in this migrations folder simultaneously:

1. `001_init_schema.sql` — an early draft using schema-qualified tables
   (`citizen_registry.citizens`, `auth.users`, `employment.employment_records`,
   `health.*`, `education.*`, `id_management.national_ids`, `reporting.audit_logs`).
   Plaintext `email`/`phone` on the citizen record, no ward FK hierarchy, no
   encryption columns. Does **not** match the SDD.

2. `V1__initial_schema.sql` — flat `public` schema (`province`, `municipality`,
   `ward`, `users`, `citizen`), fully encrypted PII columns, ward-scoped RLS
   design. **This matches the System Design Document and the JPA entities in
   the `citizen-registry` module.** This is the canonical schema going forward.

3. `V9__rls_policies_and_roles.sql` — created a *third* copy of the hierarchy
   under `auth.provinces` / `auth.municipalities` / `auth.wards` (plural,
   schema-qualified) purely to attach RLS policies to, disconnected from both
   of the above.

If all three had been applied to the same database, the app would have ended
up with three disconnected copies of "wards," and RLS would have been
filtering a table the application never actually reads from.

## Resolution

- `001_init_schema.sql` has been renamed to
  `001_init_schema.sql.SUPERSEDED_DO_NOT_APPLY` and is excluded from Flyway's
  scan (Flyway only picks up files matching `V<version>__description.sql` or
  configured baseline patterns — this file no longer matches that pattern).
  It is kept in version control for history only. **Do not restore it.**
- `V9__rls_policies_and_roles.sql` has been rewritten (see file) to attach RLS
  directly to the canonical `public.citizen` table created in `V1`, using the
  canonical `public.province` / `public.municipality` / `public.ward` tables —
  no more duplicate `auth.*` hierarchy tables.
- Going forward, **`V1__initial_schema.sql`'s design is authoritative.** Any
  new migration must extend `public.province` / `public.municipality` /
  `public.ward` / `public.users` / `public.citizen` — never re-create them
  under a different schema.

## Naming convention going forward

Flyway migrations must be named `V<N>__description.sql` with a plain integer
version (no leading zeros, no `.` in the version, e.g. `V15__...sql` not
`v11_...sql` or `V2_family_link.sql`). Two existing files violate this and
should be renamed at the next safe opportunity (test in staging first, since
renaming an *already-applied* Flyway migration changes its checksum):
- `V2_family_link.sql` → should become part of the `V10` numbering range or be
  renamed to strictly follow convention before it is ever applied in any
  environment.
- `v11_SyncRecord.sql` → same issue (lowercase `v`, underscore instead of
  double-underscore).

If these have **never been applied to any real database yet** (dev/staging/prod),
renaming now is safe. If they have already been applied anywhere, do not
rename — add a new corrective migration instead.
