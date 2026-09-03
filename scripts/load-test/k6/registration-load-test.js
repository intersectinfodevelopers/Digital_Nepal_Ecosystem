// ============================================================================
// Load test — 500 concurrent citizen registrations
//
// Targets POST {BASE_URL}/v1/citizens/register (np.gov.digital.citizen.
// controller.CitizenController#registerCitizen), which requires a
// WARD_ADMIN or LOCAL_BODY_ADMIN JWT (@PreAuthorize).
//
// WHY 500, spread across only 9 wards: the Kummayak pilot is 9 wards, but
// this is the exact code path every one of the 753 municipalities at
// national rollout will hit. 500 concurrent submissions against 9 wards
// is a deliberately harder shape than a real single ward will ever see —
// it's a stand-in for "many wards nationally, all mid-morning, all at
// once" without needing 753 sets of ward-admin fixtures to prove the
// point.
//
// WHAT SATURATES FIRST (from reading the code, before running this):
//   - HikariCP pool is capped at 20 connections (application.yml:
//     spring.datasource.hikari.maximum-pool-size: 20). With 500 concurrent
//     requests each opening a transaction (CitizenService.registerCitizen
//     is @Transactional and does a duplicate lookup + a Citizen insert +
//     an audit-log write + a GIS write inside that one transaction), we
//     expect queueing/timeouts once in-flight requests exceed ~20, not a
//     graceful linear slowdown.
//   - The container is capped at 1 CPU / 512MB heap (docker-compose.yml),
//     and every registration does AES-256 encryption on ~7 fields plus an
//     HMAC computation (NidEncryptionUtil) before the insert — CPU-bound
//     work that competes directly with Postgres connection wait time.
//
// USAGE
//   BASE_URL=http://localhost:8080/api \
//   WARD_ADMIN_PASSWORD='LoadTest!2026' \
//     k6 run scripts/load-test/k6/registration-load-test.js
//
// Requires the load-test fixtures from scripts/load-test/seed-load-test-data.sql
// to already be applied (9 wards, 9 ward-admin logins).
// ============================================================================

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const WARD_ADMIN_PASSWORD = __ENV.WARD_ADMIN_PASSWORD || 'LoadTest!2026';
const TOTAL_REGISTRATIONS = Number(__ENV.TOTAL_REGISTRATIONS || 500);
const CONCURRENCY = Number(__ENV.CONCURRENCY || 500);

// The 9 wards seeded by scripts/load-test/seed-load-test-data.sql.
const WARDS = [
    { no: 1, id: 'c3d4e5f6-a7b8-9012-cdef-123456789012', email: 'ward1.admin@loadtest.kummayak.gov.np' },
    { no: 2, id: 'd4e5f6a7-b8c9-0123-def0-234567890123', email: 'ward2.admin@loadtest.kummayak.gov.np' },
    { no: 3, id: '9cce9e6b-6cca-5fd1-8f1e-e4b6e90e6a04', email: 'ward3.admin@loadtest.kummayak.gov.np' },
    { no: 4, id: 'e64f2041-d926-5a1c-8f3f-a2e182cf4f8c', email: 'ward4.admin@loadtest.kummayak.gov.np' },
    { no: 5, id: 'ea1ec0ab-adb1-5ee8-80ac-5ea0d59dd678', email: 'ward5.admin@loadtest.kummayak.gov.np' },
    { no: 6, id: '87f18d6b-17b1-5f7c-95a3-6a97f8c48a96', email: 'ward6.admin@loadtest.kummayak.gov.np' },
    { no: 7, id: '6f134ea2-622f-5ba4-8f42-7c6c9587bfeb', email: 'ward7.admin@loadtest.kummayak.gov.np' },
    { no: 8, id: 'c13e69a3-4cb2-5446-986f-45f100c2527b', email: 'ward8.admin@loadtest.kummayak.gov.np' },
    { no: 9, id: 'd04fcc6e-3fa6-5b9c-ad28-b5315ead1ef6', email: 'ward9.admin@loadtest.kummayak.gov.np' },
];

const registrationErrors = new Counter('registration_errors');
const duplicateNidConflicts = new Counter('duplicate_nid_409s');
const registrationDuration = new Trend('registration_duration', true);

export const options = {
    scenarios: {
        // Literal reading of "500 concurrent citizen registrations": 500 VUs,
        // each submits exactly one registration, all starting together.
        citizen_registration_burst: {
            executor: 'shared-iterations',
            vus: CONCURRENCY,
            iterations: TOTAL_REGISTRATIONS,
            maxDuration: '5m',
        },
    },
    // k6 only reports avg/min/med/max/p(90)/p(95) by default — add p(99)
    // explicitly so handleSummary below doesn't print "undefined".
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        // Placeholders — tune to your actual SLA once you have a baseline run.
        http_req_failed: ['rate<0.01'],
        'registration_duration': ['p(95)<3000', 'p(99)<5000'],
    },
};

// Logs in as all 9 seeded ward admins once, shared across all VUs.
export function setup() {
    const tokensByWard = {};
    for (const ward of WARDS) {
        const res = http.post(
            `${BASE_URL}/v1/auth/login`,
            JSON.stringify({ email: ward.email, password: WARD_ADMIN_PASSWORD }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (res.status !== 200) {
            fail(
                `Setup login failed for ${ward.email}: HTTP ${res.status} ${res.body}. ` +
                `Did you run scripts/load-test/seed-load-test-data.sql against this DB?`
            );
        }
        tokensByWard[ward.no] = res.json('accessToken');
    }
    return { tokensByWard };
}

function randomDigits(n) {
    let s = '';
    for (let i = 0; i < n; i++) s += Math.floor(Math.random() * 10);
    return s;
}

function buildRegistrationPayload(ward, uniqueSuffix) {
    return {
        wardId: ward.id,
        nid: `LT-${uniqueSuffix}`,
        citizenshipNo: `CIT-${uniqueSuffix}`,
        nameNp: 'लोड टेस्ट नागरिक',
        nameEn: `Load Test Citizen ${uniqueSuffix}`,
        dob: '1990-01-01',
        sex: ['MALE', 'FEMALE', 'OTHER'][Math.floor(Math.random() * 3)],
        digitalLiteracy: 'BASIC',
        hasSmartphone: true,
        consentChannel: 'FIELD',
        registrationChannel: 'MOBILE_APP',
        deviceId: `loadtest-device-ward${ward.no}`,
        gps: {
            latitude: 26.45 + Math.random() * 0.1,
            longitude: 87.28 + Math.random() * 0.1,
            accuracyM: 5,
        },
    };
}

export default function (data) {
    // Spread load across all 9 wards round-robin by VU id, matching the
    // "many wards submitting at once" shape rather than hammering one ward.
    const ward = WARDS[(__VU - 1) % WARDS.length];
    const token = data.tokensByWard[ward.no];

    // __VU + __ITER + a random component keeps NID/citizenshipNo unique
    // across the whole run so we're measuring registration throughput, not
    // artificially generating 409 DUPLICATE_NID responses against ourselves.
    const uniqueSuffix = `${__VU}-${__ITER}-${randomDigits(6)}`;
    const payload = buildRegistrationPayload(ward, uniqueSuffix);

    const res = http.post(
        `${BASE_URL}/v1/citizens/register`,
        JSON.stringify(payload),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            tags: { name: 'RegisterCitizen' },
        }
    );

    registrationDuration.add(res.timings.duration);

    const ok = check(res, {
        'status is 201': (r) => r.status === 201,
    });

    if (res.status === 409) {
        duplicateNidConflicts.add(1);
    } else if (!ok) {
        registrationErrors.add(1);
        console.error(`Registration failed: HTTP ${res.status} ${res.body}`);
    }

    sleep(0.1);
}

export function handleSummary(data) {
    return {
        'scripts/load-test/k6/results/registration-load-test-summary.json': JSON.stringify(data, null, 2),
        stdout: '\n' + textSummaryFallback(data),
    };
}

// Minimal fallback so this script has no dependency on the (optional)
// k6-summary text-summary helper library.
function textSummaryFallback(data) {
    const m = data.metrics;
    const get = (name, stat) => (m[name] && m[name].values ? m[name].values[stat] : undefined);
    return [
        'REGISTRATION LOAD TEST SUMMARY',
        `  requests:        ${get('http_reqs', 'count')}`,
        `  failed rate:     ${get('http_req_failed', 'rate')}`,
        `  duration p50:    ${get('registration_duration', 'med')} ms`,
        `  duration p95:    ${get('registration_duration', 'p(95)')} ms`,
        `  duration p99:    ${get('registration_duration', 'p(99)')} ms`,
        `  duration max:    ${get('registration_duration', 'max')} ms`,
        `  409 duplicates:  ${get('duplicate_nid_409s', 'count') || 0}`,
        `  other errors:    ${get('registration_errors', 'count') || 0}`,
    ].join('\n');
}