# Digital Nepal Ecosystem — Fixes Applied This Pass

**Important caveat first:** this sandbox cannot reach Maven Central, so none
of this was verified with `mvn compile`. Everything below was hand-reviewed
carefully (imports checked, brace-balance checked, cross-file call sites
grepped for), but a real build pass by your team — or by Claude Code running
with full network access — should happen before any of this touches staging.

---

## 1. Schema reconciliation (the foundational fix)

- Three conflicting definitions of province/municipality/ward/citizen existed
  across `001_init_schema.sql`, `V1__initial_schema.sql`, and
  `V9__rls_policies_and_roles.sql`. Retired the first, rewrote the third to
  attach RLS to the real tables from the second. See
  `db/migrations/SCHEMA_RECONCILIATION_NOTES.md` for full detail.
- `V17__citizen_edit_requests_table.sql` — created the `citizen_edit_requests`
  table, which the `auth` module's `ApprovalService`/`ApprovalController`
  already referenced in Java but which had **no migration at all**. The
  approval workflow could not have worked against a real database before this.
- `V18__extend_rls_policies.sql` — RLS was previously only enforced on
  `citizen`. Extended to `citizen_gis`, `citizen_edit_requests`, `sync_batch`,
  `sync_conflict_registry`, and `citizen_events` — the other five tables the
  SDD's Section 3.2.2 requires it on.
- `V19__align_users_table_with_entity.sql` — the `users` table (V1) and the
  `User` JPA entity had completely different column shapes (username vs.
  email, single jurisdiction_type/id vs. three separate nullable FK columns).
  Reconciled by extending the table to satisfy the entity, since login logic
  throughout `AuthService`/`CustomUserDetailsService` is consistently
  email-based. **This leaves some redundant unused columns as documented
  tech debt — see the TODO in that file for the permanent cleanup.**

## 2. Real security fixes

- **Removed a hardcoded default AES encryption key** in `NidEncryptionUtil`
  (`"12345678901234567890123456789012"`) that silently activated whenever the
  `ENCRYPTION_KEY` env var was missing. Every citizen's NID/DOB/phone
  encrypted under that default would have been decryptable by anyone with
  repo access. Now fails startup instead.
- **Implemented pepper-based `nid_hmac`**, replacing the plain SHA-256
  `nid_hash` for NID duplicate-detection (SDD Critical Implementation Note
  #2). Plain SHA-256 of a 10-digit NID is brute-forceable offline from a DB
  dump; HMAC with a pepper stored only in Vault/env is not.
  `NidEncryptionUtil.hmac()`, `Citizen.nidHmac`,
  `CitizenRepository.existsByNidHmacAndIsActiveTrue`, and `CitizenService`'s
  dedup check were all updated. `nid_hash` is kept temporarily,
  clearly marked `@Deprecated`, with a backfill plan documented in
  `V16__nid_hmac_pepper_migration.sql`.
- **Built the real `citizen_events` append-only audit table**
  (`V15__citizen_events_audit_log.sql`) — previously just an empty placeholder
  file. Enforced append-only at two layers: `REVOKE UPDATE, DELETE` from the
  app's DB role, plus a `BEFORE UPDATE OR DELETE` trigger that raises an
  exception unconditionally.
- **Fixed `AuditLogService`**, which was inserting into
  `reporting.audit_logs` — a table that only ever existed in the abandoned
  `001_init_schema.sql` draft. Every audit call across the codebase
  (duplicate-NID attempts, approval decisions, etc.) has been silently
  failing since the try/catch swallows the error by design. Redirected to
  the real `citizen_events` table, with actor role/jurisdiction extraction
  from JWT claims.
- **Converted JWT signing from HS256 to real RS256.** `JwtService` was using
  `Keys.hmacShaKeyFor(...)` — a *symmetric* algorithm — despite the SDD
  explicitly requiring RS256 specifically so that verifying a token never
  requires the power to mint one. Rewrote to load an RSA key pair (PEM,
  from Vault/env or a `file:` path for dev) and sign/verify asymmetrically.
- **Found and fixed a missing `jwt:` config block entirely.** `JwtProperties`
  (`secret`/`accessTokenExpiration`/`refreshTokenExpiration`) was never
  configured anywhere — `jwtProperties.getSecret()` would have returned
  `null`, meaning the very first login attempt would NPE inside
  `Decoders.BASE64.decode(null)`. Login could not have worked end-to-end
  before this fix.
- **Removed hardcoded DB credentials** (`password: root`, no env var at all)
  from `modules/auth/src/main/resources/application.yml`.
- **Stopped defaulting `DB_PASSWORD` to `"postgres"`** in the base
  application.yml — that default now only exists in the `dev` profile block,
  so staging/prod fail fast instead of silently connecting with a guessable
  password.
- **Removed `env` and `loggers` from the exposed actuator endpoints** — no
  actuator-specific auth/network restriction existed anywhere in this
  project, so `/actuator/env` would have been a near-direct path to leaking
  `ENCRYPTION_KEY`/`PEPPER_SECRET` (Spring Boot's default property-name
  redaction wouldn't have caught `app.pepper`).
- **Switched `ddl-auto` from `update` to `validate`** in the base and `dev`
  profiles, now that Flyway is properly wired in as the schema source of
  truth — `update` running alongside Flyway risks silent schema drift.

## 3. Made the application actually runnable

This was the single biggest structural finding: **only the standalone `auth`
module had a `@SpringBootApplication` main class.** Every other module
(`citizen-registry`, `platform-grievance`, `platform-sync`, `platform-idcard`,
`platform-audit`, `platform-gis`, `household`, `employment`) is a pure
library JAR — `@RestController`/`@Service`/`@Entity` classes with nothing to
boot them into a running server — and the root `pom.xml` is
`packaging=pom` (an aggregator, not itself runnable).

Created `modules/bootstrap`: a new module depending on every business module,
with the one `@SpringBootApplication` entry point
(`DigitalNepalEcosystemApplication`) and the single shared `application.yml`
(moved here from the root, which was never actually loaded by anything since
`pom`-packaged modules don't execute). This matches the SDD's own stated
architecture — "a monolithic Spring Boot 3 application... designed for
microservice extraction when scale demands it" — one deployable, not N
independently-running, mutually-unreachable services.

**Follow-up decision needed from your team:** the `auth` module's own
standalone `AuthApplication.java` + its own `application.yml` + its own
Flyway/datasource config are now redundant with `bootstrap`. Once you confirm
the monolith model, retire them (or keep `auth` independently runnable on
purpose, if you actually want a real microservice split — in which case
`auth` would need to expose a JWKS/OIDC-discovery endpoint so other services
can validate tokens it issues, which is a materially bigger feature).

## 4. Fixed a systemic URL-path bug

Almost every controller across almost every module hardcoded a `/api` prefix
in its own `@RequestMapping` (e.g. `/api/v1/auth`), while the global
`server.servlet.context-path` is *also* `/api`. Combined, this doubles the
prefix (`/api/api/v1/auth/...`) — only `citizen-registry`'s three controllers
had it right. Fixed in: `ApprovalController`, `AuthController`,
`GrievanceController`, `ForeignEmploymentController`, `EmploymentController`,
`HouseholdController`, `IdCardController`, `SyncController`,
`AuditLogController`. Also fixed `SecurityConfig`'s `permitAll()` matchers,
which referenced the old doubled paths and would not have actually matched
the real request path — meaning `/auth/login` was likely not reachable as
documented, or the security rule intended for it silently never applied.

## 5. Smaller fixes

- Wired the `household` module into the root `pom.xml`'s `<module>` list —
  it had full source but was never part of the Maven build.
- Removed a duplicate `platform-audit` dependency declaration (two different
  version expressions) in `citizen-registry`'s `pom.xml`, and fixed a
  hardcoded `1.0.0` version pin to use `${project.version}` consistently.
- `DuplicateNidException` no longer carries a hash-like value through
  exception-handling layers/logs for no operational reason.

---

## What's still genuinely missing (not fixed this pass — scope/judgment calls)

1. **Admin-provisioning endpoints** (`POST /admin/ward-admins`,
   `/admin/local-body-admins`, `/admin/province-admins`, disable/enable,
   reset-password) — discussed at length in this conversation, not yet
   built. This is the next concrete piece: the four-tier hierarchy currently
   has no way to create accounts except direct DB inserts.
2. **Grievance public tracking endpoint** — `GET /grievances/track/{code}`
   from SDD Section 6.6 does not exist in `GrievanceController` at all.
3. **WebAuthn/FIDO2 phishing-resistant MFA** — discussed as the recommended
   top-tier auth mechanism; not yet scaffolded in code.
4. **`education` and `reporting` modules** — still empty shells.
5. **`platform-disability`/`platform-eligibility`** — still just marker
   classes; real logic lives in `citizen-registry` instead. Module
   boundaries don't match the SDD; not reconciled this pass.
6. **`id-management`** — still an empty duplicate of `platform-idcard`; needs
   a decision (delete vs. merge), not new code.
7. Failed-login lockout logic (5 attempts → 30 min lock per SDD 3.5) has the
   right *fields* (`failedAttempts`, `lockTime`, `accountNonLocked`) and
   Spring Security correctly reads them via `CustomUserDetails`, but nothing
   in `AuthService` currently *increments* `failedAttempts` or *sets*
   `lockTime`/`accountNonLocked` on a failed login, or clears them after the
   lock window passes. Noted but not implemented this pass.
