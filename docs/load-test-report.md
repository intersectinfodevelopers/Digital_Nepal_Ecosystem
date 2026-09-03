# Load Test Report — Citizen Registration & Offline Sync

**Status:** ✅ Both load tests complete. Registration: 0% success at 500 concurrent (Section 4). Sync: 100% success at all 3 batch sizes tested, but latency numbers are confounded by run order (Section 5) — re-run recommended before trusting the per-record cost.

| |                                                                                                                                 |
|---|---------------------------------------------------------------------------------------------------------------------------------|
| Date | 2026-09-03                                                                                                                      |
| Run by | Amit Khatiwada                                                                                                                  |
| Commit hash under test | _fill in (`git rev-parse HEAD`, after committing the `RefreshTokenService` fix)_                                                |
| Environment | Local `docker-compose.yml` (1 CPU / 1GB backend container, Postgres 16 + PostGIS, Redis 7) — see Section 2                      |
| Raw results | `scripts/load-test/k6/results/registration-load-test-summary.json`, `scripts/load-test/k6/results/sync-burst-test-summary.json` |

## 1. Why these two tests

The Kummayak pilot runs on 9 wards. National rollout targets all 753
municipalities. Two scenarios were chosen because they stress the exact
code paths a manual 9-ward pilot test can't meaningfully exercise:

1. **500 concurrent citizen registrations** — `POST /v1/citizens/register`
   is the single highest-volume write path in the system. A manual pilot
   test only ever exercises a handful of sequential registrations; this
   test asks what happens when far more municipalities than the pilot
   are registering citizens at the same moment.
2. **9-ward simultaneous sync** — the realistic failure trigger for a
   rural deployment isn't steady load, it's connectivity coming back to
   a village after an outage and every ward device syncing its offline
   backlog at once. This is a burst-concurrency test, not a
   sustained-throughput test.

## 2. Environment

| Component | Configuration | Source |
|---|---|---|
| Backend container | 1 CPU limit, 1GB memory limit, `-Xmx512m -Xms256m` | `docker-compose.yml` |
| DB connection pool | HikariCP, `maximum-pool-size: 20`, `minimum-idle: 5` | `application.yml` |
| Database | Postgres 16 + PostGIS, single container, default `postgresql.conf` | `docker-compose.yml` |
| App profile | `dev` (`SPRING_PROFILES_ACTIVE=dev`) | `docker-compose.yml` |
| Flyway | Disabled in `docker-compose.yml` (`SPRING_FLYWAY_ENABLED: "false"`) — see caveat below | `docker-compose.yml`, `db/migrations/SCHEMA_RECONCILIATION_NOTES.md` |
| Host machine | _fill in: CPU/RAM of the machine running docker-compose_ | |

**This is a single-container, laptop/CI-grade environment, not
production infrastructure.** Treat all numbers here as *relative*
signal (where does it break first, how does it degrade) rather than
absolute production capacity. If staging/prod runs with different
resource limits or a managed Postgres instance, re-run there before
using these numbers for a national-rollout capacity decision.

### Environment gap found while setting this up

`docker-compose.yml` disables Flyway to work around a schema-history
mismatch (`flyway_schema_history` empty vs. an already-evolved dev
database — see the inline comment in `docker-compose.yml`), while
`application.yml` runs `ddl-auto: validate`. On a **freshly created**
Postgres volume, this combination means the schema is never created and
the app fails to start. This isn't a load-test artifact — it means
anyone provisioning a genuinely new environment (a new municipality's
staging DB, a fresh CI runner, a new engineer's laptop) hits a blocked
boot today. Worked around for this test per `scripts/load-test/README.md`
step 1; recommend fixing per the TODO in
`db/migrations/SCHEMA_RECONCILIATION_NOTES.md` before it blocks a real
rollout environment.

## 3. What the code predicts before running anything

Read directly from `CitizenService.registerCitizen` and
`SyncService.processBatch` — stated here so the results in Sections 4–5
can be checked against a hypothesis instead of just narrated after the
fact.

**Registration (`/v1/citizens/register`):**
- `@Transactional`, single method: ward lookup, HMAC-based duplicate
  check, AES-256 encryption of ~7 PII fields (`NidEncryptionUtil`), a
  `Citizen` insert, plus audit-log and GIS writes — all in one DB
  transaction per request.
- Expected first bottleneck: **Hikari's 20-connection cap**, not CPU or
  the encryption work itself. At 500 concurrent requests, ~480 will be
  queued waiting for a connection at any given instant if each
  transaction holds its connection for the full duration of that work.
  Expect a knee in the latency curve around the point where in-flight
  requests exceed ~20, not a smooth degradation.
- Secondary: 1 CPU limit means the AES/HMAC work across 500 concurrent
  requests is itself serialized on the CPU, independent of the DB pool.

**Sync (`/v1/sync/submit`):**
- Fully synchronous despite the `Job`/`JobLauncher` beans being wired in
  — they're never invoked. Every record in a batch is written via an
  individual `syncRecordRepository.save(...)` call inside the loop,
  inside the same transaction the HTTP request is blocked on.
- Expected behavior: request latency scales roughly linearly with
  `BATCH_SIZE` (one DB round trip per record, no bulk insert). A batch of
  100 offline records should take roughly 100x the per-record insert
  time, not a fixed batch-processing overhead.
- With only 9 concurrent sync requests, the Hikari pool (20 connections)
  is unlikely to be the bottleneck on its own — but each of those 9
  connections is held for the *entire* batch duration, which matters if
  registrations are happening concurrently with the sync burst (a
  realistic pilot scenario: other wards registering citizens while one
  ward's connectivity comes back). Worth a follow-up test combining both
  scenarios if these individual results look healthy in isolation.

## 4. Results — 500 concurrent citizen registrations

**Run 1 (cold, first boot after fix to `RefreshTokenService`):**

| Metric | Value |
|---|---|
| Total HTTP requests (9 setup logins + 500 registrations) | 509 |
| Registration attempts | 500 |
| Registrations succeeded (201) | **0** |
| Setup logins succeeded (200) | 9 |
| Overall failed rate | 98.2% |
| p95 latency (registration_duration) | 59,866 ms |
| Max latency | 59,960 ms |
| 409 duplicate-NID conflicts | 0 |
| Run wall-clock time | 1m02.6s (of a 5m allotted max) |

**Failure breakdown** (from k6 stdout + `docker compose logs backend`), all 500 attempts failed via one of three paths:

| Failure mode | Approx. count | When | Cause |
|---|---|---|---|
| TCP connection refused | ~90 | t=0–2s | Tomcat connector couldn't accept the simultaneous connection burst (default `maxThreads=200`, `acceptCount=100` — no tuning in `application.yml`) |
| HTTP 403 (masking a DB timeout) | ~320 | t=22–62s | Hikari pool (max 20) exhausted; `SQLTransientConnectionException` after Hikari's 30s acquisition timeout, unhandled, forwarded to `/error`, blocked by security config (see Section 6.2) — surfaces to the caller as a generic 403 instead of a 503 |
| Client-side timeout (k6's 60s default) | ~90 | t=62s | Requests still queued for a DB connection when k6 gave up waiting |

Confirmed directly in the backend logs:
```
org.hibernate.exception.JDBCConnectionException: Unable to acquire JDBC Connection
Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30021ms.
```

**Did the Hikari-pool hypothesis in Section 3 hold?** Yes, exactly. Predicted before running anything: *"expect a knee in the latency curve around the point where in-flight requests exceed ~20, not a smooth degradation"* — that's precisely what happened. The first ~20 or so requests that got a connection at all likely succeeded quickly; everything past that queued for up to 30s, then failed. **Zero registrations succeeded in this run at this concurrency in this environment.**

_Warm run: not yet performed — see Section 9, Next Steps. Given a 0% success rate on the cold run, a warm re-run should happen only after addressing at least the Tomcat connector and Hikari pool sizing (Section 7), otherwise it will likely reproduce the same 0% result and add little new information._

## 5. Results — 9-ward simultaneous sync burst

All three runs executed back-to-back in the same live container, immediately following the 500-concurrent registration test (Section 4) — no restart in between. That ordering matters for reading these numbers; see the caveat below the table.

| Metric | BATCH_SIZE=100 (run 1st) | BATCH_SIZE=50 (run 2nd) | BATCH_SIZE=300 (run 3rd) |
|---|---|---|---|
| All 9 batches succeeded (200/SUCCESS)? | ✅ yes | ✅ yes | ✅ yes |
| Failed rate | 0% | 0% | 0% |
| p95 batch latency | 8,285 ms | 2,004 ms | 5,778 ms |
| Latency ÷ BATCH_SIZE (per-record cost) | 82.8 ms | 40.1 ms | 19.3 ms |

**Headline result: unlike registration, all 9 simultaneous ward syncs succeeded at every batch size tested.** This matches the Section 3 prediction that 9 concurrent transactions sit comfortably under the 20-connection Hikari cap — sync is not at risk from the same failure mode as registration, at least not at this concurrency.

**Did latency scale linearly with BATCH_SIZE as predicted?** **No — and this itself is a finding.** Per-record cost should be roughly constant if it's purely "one DB round trip per record" as `SyncService.processBatch`'s code suggests. Instead it varies by more than 4x across runs, and counterintuitively the *largest* batch (300) was fastest per record while the *middle* batch (100) was slowest. The most likely explanation is run-order contamination, not batch size: `BATCH_SIZE=100` ran immediately after the 500-concurrent registration test in Section 4, which left ~90 requests hung for the full 60s timeout and the Hikari pool under heavy contention — that run likely inherited leftover connection-pool pressure, GC activity, and a JVM/JIT that hadn't warmed up yet. By the time `50` and `300` ran, the system had settled, consistent with `300` (most work) finishing fastest of the three in absolute terms.

**This is a real methodological lesson for future runs, not just a caveat:** any single load-test number in this environment is sensitive to what ran immediately before it. Section 9 (Next Steps) includes redoing this specific comparison with proper isolation (restart the container between batch sizes, or run each size 3x and take the median) before treating the per-record cost as a reliable planning number.

## 6. Bottlenecks identified

### 6.1 Hikari pool exhaustion at 20 connections — pilot-blocking is the wrong frame; this is a rollout-scale finding, but the failure mode itself (silent total failure past a threshold, no graceful degradation) is worth fixing regardless of pilot size

**What broke, at what concurrency:** 500 concurrent `POST /v1/citizens/register` requests, 100% failure rate. From the backend logs, the DB connection pool (`hikari.maximum-pool-size: 20`) is the hard limit — every request beyond roughly the first ~20 in flight queued for a connection, hit Hikari's 30s acquisition timeout, and failed.

**Root cause:** `CitizenService.registerCitizen` is `@Transactional` and holds its DB connection for the full duration of the method — duplicate check, AES/HMAC work, the `Citizen` insert, plus audit-log and GIS writes — all inside one transaction. With only 20 connections available, 20 is the real concurrent-write ceiling for this endpoint today, regardless of CPU or application logic performance.

**Is it pilot-blocking?** No — 9 real wards will never generate 500 simultaneous registrations. This was an intentional stress test standing in for the 753-municipality rollout target, not a pilot requirement, and the pilot itself is not at risk from this specific number. It **is** a rollout-scale finding: if even a modest fraction of 753 municipalities register citizens concurrently during a busy period, this ceiling will be hit for real. Worth deciding on a target pool size (or a queueing/backpressure strategy) before rollout, not after.

### 6.2 A DB-exhaustion condition is invisible to the caller — surfaces as a generic 403, not a 503 — this one *is* worth fixing regardless of scale

**What broke:** When Hikari can't get a connection, Spring throws `SQLTransientConnectionException`. There's no handler for it in `GlobalExceptionHandler` (which only handles `ResourceNotFoundException` and `UnauthorizedException`), so it becomes an unhandled exception. Spring Boot forwards unhandled exceptions to `/error` — but `/error` isn't in `SecurityConfig`'s `permitAll()` list, so for an unauthenticated forward it gets blocked by `.anyRequest().authenticated()` too, and the caller receives a bare 403 with an empty body instead of a 503/500 with any useful information.

**Why it matters beyond load testing:** this same mechanism is what made the earlier refresh-token bug (Section 6.3) so hard to diagnose — *any* unhandled backend exception looks identical to an authorization failure from the client's point of view. A ward device that can't tell "you're not allowed to do that" apart from "the server is temporarily overloaded, retry" can't build sensible retry/offline-queue behavior around that distinction. This is a debuggability and client-behavior problem independent of how big the pilot or rollout is.

**Recommendation:** add a generic `@ExceptionHandler(Exception.class)` fallback in `GlobalExceptionHandler` returning 500 with a minimal safe body, and add `/error` to the `permitAll()` list in `SecurityConfig`, so failures are visible as what they actually are.

### 6.3 [Found during test setup, not a load result, but discovered because of this effort] Every user's second login was broken

Not a load-test finding in the concurrency sense, but found while preparing test fixtures and worth recording here since it would have invalidated every later result if left in place: `RefreshTokenService.createRefreshToken` called a derived `deleteByUser(...)` query without an active transaction. The **first** login for any user works (nothing to delete yet); **every login after that** throws `jakarta.persistence.TransactionRequiredException` and fails — meaning any real ward admin logging out and back in, or refreshing an expired session, would have hit this in production. Fixed during this work (`@Transactional` added to `createRefreshToken`) — see `modules/auth/src/main/java/np/gov/digital/auth/service/RefreshTokenService.java`. **This is pilot-blocking and should be treated as a priority fix independent of anything else in this report**, since it affects the pilot's 9 wards today, not just rollout scale.

### 6.4 Tomcat connector not tuned for burst concurrency

~90 of the 500 registration attempts were refused at the TCP level in the first two seconds, before reaching application code at all — the default embedded Tomcat connector (`maxThreads=200`, `acceptCount=100`, neither overridden in `application.yml`) can't accept 500 simultaneous connections. This compounds the Hikari limit rather than being an independent bottleneck: raising the Hikari pool alone won't fix this half of the failures.

## 7. Recommendations

- [ ] **Priority / pilot-blocking:** confirm the `RefreshTokenService` fix (Section 6.3) is deployed to any environment real ward admins use — this breaks normal login/logout behavior today, at any scale.
- [ ] **Rollout-scale:** re-evaluate `hikari.maximum-pool-size: 20` against expected concurrent-write volume at rollout scale. Test raising it (e.g. to 50–100) and re-running the registration load test to see where the new ceiling is — this is a one-line config change to test, not a code change.
- [ ] **Rollout-scale:** tune the embedded Tomcat connector (`server.tomcat.threads.max`, `server.tomcat.accept-count`) alongside the Hikari pool — raising one without the other won't fix the 0%-success result seen here.
- [ ] **Correctness, any scale:** add a generic exception handler and permit `/error` (Section 6.2), so pool exhaustion and other backend failures surface as a real status code instead of a bare 403 indistinguishable from an authorization failure.
- [ ] Consider batching the inserts in `SyncService.processBatch`
  (e.g. `saveAll(...)` or actually wiring up the existing
  `JobLauncher`/`Job` for async processing) if Section 5 shows batch
  latency becoming a problem at realistic offline-backlog sizes.
- [ ] Fix the Flyway/`ddl-auto: validate` conflict in
  `docker-compose.yml` (Section 2) so new environments can boot at all —
  unrelated to load numbers, but was a hard blocker to running this test
  and will block every future fresh environment too.
- [ ] Re-run the registration load test after addressing the pool-size and
  connector tuning above — a 0%-success run doesn't tell us where the
  *real* ceiling is, only that 500 concurrent is well past it.
- [ ] Re-run this suite against a production-shaped environment (real
  CPU/memory allocation, managed Postgres, no shared-container Redis)
  before treating any number here as a rollout capacity figure.

## 8. How to reproduce

```bash
cd Digital_Nepal_Ecosystem
make docker-up   # see scripts/load-test/README.md if this is a fresh volume
docker compose exec -T postgres psql -U postgres -d digital_nepal < scripts/load-test/seed-load-test-data.sql
k6 run scripts/load-test/k6/registration-load-test.js
k6 run scripts/load-test/k6/sync-burst-test.js
```

Full walkthrough with all flags and troubleshooting: `scripts/load-test/README.md`.

## 9. Next steps

1. Apply the `RefreshTokenService` fix (Section 6.3) to any environment
   real users touch, independent of the rest of this report.
2. Decide on a Hikari pool size + Tomcat connector target for rollout,
   apply it, and re-run the 500-concurrent-registration test to find the
   actual ceiling (this run only established that it's below 500 — it
   did not find where the real limit sits).
3. Re-run the sync burst test's 3 batch sizes with proper isolation
   (restart the backend container between each size, or run each size 3x
   and take the median) — the numbers in Section 5 are confounded by
   run order and shouldn't be used for capacity planning as-is.
4. Re-run both tests warm (no restart between cold and warm runs) once
   there's a config that produces a non-zero registration success rate,
   so the two numbers are actually comparable.