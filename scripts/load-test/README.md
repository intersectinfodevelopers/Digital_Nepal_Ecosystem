# Day 16 Load Testing — Run Guide

Step-by-step to run both load tests locally against `docker-compose.yml`
and produce the numbers for `docs/load-test-report.md`.

Run these from the repo root (`Digital_Nepal_Ecosystem/`).

## 0. Prerequisites

- Docker + Docker Compose v2 (`docker compose version`)
- [k6](https://k6.io) installed locally:
    - macOS: `brew install k6`
    - Linux: `sudo gpg -k && sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69 && echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list && sudo apt-get update && sudo apt-get install k6`
    - Windows: `choco install k6`
- `.env` present at repo root with `ENCRYPTION_KEY` and `PEPPER_SECRET` set (already present in this repo for local dev — do not reuse these values anywhere but a throwaway local/CI DB).

## 1. Start the stack

```bash
cp .env.example .env   # skip if .env already exists — it does in this repo
make docker-up
```

Wait for `make docker-up`'s built-in `health-check` to pass. If the backend container keeps restarting, check step 2 below before anything else — it's a known gap in a fresh environment.

**⚠️ Known gap found while building this test:** `docker-compose.yml` sets
`SPRING_FLYWAY_ENABLED: "false"` (worked around a schema-history mismatch —
see the comment in `docker-compose.yml` and `db/migrations/
SCHEMA_RECONCILIATION_NOTES.md`), and `application.yml` runs
`ddl-auto: validate`. On a **brand-new** Postgres volume, that combination
means: Flyway never creates the schema, and Hibernate then fails to
validate entities against a database with no tables — the backend will
not start. This only doesn't bite existing dev machines because their
Postgres volume already has the fully-evolved schema from before Flyway
was disabled.

For a clean load-test environment (fresh volume, CI, or anyone spinning
this up for the first time), apply migrations once with Flyway
temporarily re-enabled:

```bash
docker compose down -v          # start from a truly clean volume
docker compose up -d postgres
SPRING_FLYWAY_ENABLED=true docker compose up -d backend
make health-check
```

This should be fixed properly (reconcile `flyway_schema_history` per the
notes in `db/migrations/SCHEMA_RECONCILIATION_NOTES.md`) before national
rollout — a fresh environment that can't boot is itself a pilot-readiness
finding, not just a load-test setup step. It's called out in the report.

## 2. Apply load-test fixtures

The seed migration (`V1__initial_schema.sql`) only creates Ward 1 and
Ward 2, and no `users` rows at all — there's no self-service account
creation endpoint, so without this step there is no way to obtain a
`WARD_ADMIN` JWT to call the registration or sync endpoints.

```bash
docker compose exec -T postgres psql -U postgres -d digital_nepal \
  < scripts/load-test/seed-load-test-data.sql
```

This adds wards 3–9 and one `WARD_ADMIN` per ward (login: `wardN.admin@loadtest.kummayak.gov.np`, password `LoadTest!2026` — see the SQL file's header comment for why these live here and not in a Flyway migration).

Verify:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ward1.admin@loadtest.kummayak.gov.np","password":"LoadTest!2026"}'
```

You should get back a 200 with `accessToken` populated. If you get 401,
re-check step 2 ran against the right database/container.

## 3. Run the registration load test (500 concurrent)

```bash
mkdir -p scripts/load-test/k6/results
BASE_URL=http://localhost:8080/api \
WARD_ADMIN_PASSWORD='LoadTest!2026' \
  k6 run scripts/load-test/k6/registration-load-test.js
```

Optional knobs: `TOTAL_REGISTRATIONS` and `CONCURRENCY` (both default 500).

This writes `scripts/load-test/k6/results/registration-load-test-summary.json`
and prints a summary to stdout. **Watch the backend logs in a second
terminal while this runs** (`make logs`) — Hikari pool exhaustion shows up
there as `Connection is not available, request timed out` before it shows
up as a k6-side failure.

## 4. Run the 9-ward simultaneous sync burst

```bash
BASE_URL=http://localhost:8080/api \
WARD_ADMIN_PASSWORD='LoadTest!2026' \
BATCH_SIZE=100 \
  k6 run scripts/load-test/k6/sync-burst-test.js
```

`BATCH_SIZE` is the number of offline records each of the 9 simulated
ward devices submits in its one sync call — i.e. how big a backlog that
ward built up while disconnected. 100 is a starting guess; if you have
real numbers from the Flutter app's offline queue, use those instead and
re-run at a couple of sizes (e.g. 50 / 100 / 300) since
`SyncService.processBatch` does one DB round trip per record, so latency
should scale with this number — see the comment block at the top of
`sync-burst-test.js`.

This writes `scripts/load-test/k6/results/sync-burst-test-summary.json`.

## 5. Repeat once, cold vs warm

Run both tests twice: once immediately after `make docker-up` (cold
JVM/JIT, cold connection pool) and once again a few minutes later with no
restart in between (warm). Record both — a pilot ward's first sync of the
day after overnight downtime is closer to the "cold" number.

## 6. Write up the results

Fill in the results tables in `docs/load-test-report.md` from the two
JSON summaries and your terminal output. Keep the raw JSON files
(`scripts/load-test/k6/results/*.json`) — commit them alongside the
report so the numbers are reproducible/auditable, not just narrated.

## Cleanup

```bash
docker compose exec -T postgres psql -U postgres -d digital_nepal -c \
  "DELETE FROM users WHERE username LIKE 'loadtest.%'; \
   DELETE FROM sync_record WHERE payload LIKE '%SYNC-%'; \
   DELETE FROM sync_batch WHERE device_id LIKE 'ward%-tablet-01'; \
   DELETE FROM citizen WHERE nid_enc IS NOT NULL AND ward_id IN (SELECT id FROM ward WHERE ward_no > 2);"
```

(Adjust if you've since registered real data in wards 3–9 for other
purposes — this is a blunt cleanup for a dedicated load-test DB only.)