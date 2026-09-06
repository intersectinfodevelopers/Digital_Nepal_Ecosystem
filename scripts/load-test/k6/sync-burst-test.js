// ============================================================================
// Load test — simulated 9-ward simultaneous sync/submit burst
//
// Targets POST {BASE_URL}/v1/sync/submit (SyncController#submitSyncBatch ->
// SyncService#processBatch).
//
// WHY THIS SHAPE: the most realistic failure mode for a rural pilot isn't
// steady traffic — it's every ward device regaining signal at the same
// moment (connectivity restored to the village) and syncing everything
// it queued while offline, all at once. This script models that as 9 VUs
// (one per ward device), each firing exactly one sync batch simultaneously,
// where each batch contains BATCH_SIZE records (the backlog that device
// accumulated while offline).
//
// WHAT THE CODE ACTUALLY DOES (read from SyncService.processBatch before
// writing this test, so the results have a hypothesis to check against):
//   - It is fully synchronous. The injected Spring Batch JobLauncher/Job
//     are wired into SyncService but never invoked in processBatch — every
//     record in the batch is written via an individual
//     syncRecordRepository.save(...) call, in a loop, inside the same
//     @Transactional method the HTTP request is waiting on. There is no
//     async hand-off; the response only returns after every record in the
//     batch has been inserted.
//   - That means request latency should scale roughly linearly with
//     BATCH_SIZE, and a large batch holds one DB connection + one open
//     transaction for the entire duration.
//   - With 9 wards syncing simultaneously, we're really testing: can 9
//     concurrent long-held transactions (each doing BATCH_SIZE sequential
//     inserts) coexist inside a 20-connection Hikari pool without the
//     other consumers of that pool (registrations, reads) starving.
//
// USAGE
//   BASE_URL=http://localhost:8080/api \
//   WARD_ADMIN_PASSWORD='LoadTest!2026' \
//   BATCH_SIZE=100 \
//     k6 run scripts/load-test/k6/sync-burst-test.js
//
// Requires scripts/load-test/seed-load-test-data.sql to already be applied.
// ============================================================================

import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const WARD_ADMIN_PASSWORD = __ENV.WARD_ADMIN_PASSWORD || 'LoadTest!2026';
// Records per ward device's offline backlog. 100 is a rough stand-in for
// "a ward that's been offline a few days" — adjust to match real
// Flutter-app offline-queue sizes once you have field data.
const BATCH_SIZE = Number(__ENV.BATCH_SIZE || 100);

const WARDS = [
    { no: 1, id: 'c3d4e5f6-a7b8-9012-cdef-123456789012', email: 'ward1.admin@loadtest.kummayak.gov.np', userId: 'f72e59e3-b24a-59bd-ae11-e90c29c64aa9' },
    { no: 2, id: 'd4e5f6a7-b8c9-0123-def0-234567890123', email: 'ward2.admin@loadtest.kummayak.gov.np', userId: '9a5ca351-769a-553e-9a18-7cc5f8895852' },
    { no: 3, id: '9cce9e6b-6cca-5fd1-8f1e-e4b6e90e6a04', email: 'ward3.admin@loadtest.kummayak.gov.np', userId: '46732a1b-e7b5-51c9-9e93-af16e823d27d' },
    { no: 4, id: 'e64f2041-d926-5a1c-8f3f-a2e182cf4f8c', email: 'ward4.admin@loadtest.kummayak.gov.np', userId: '5ca16ed3-88d2-5965-b218-acdffb498a6e' },
    { no: 5, id: 'ea1ec0ab-adb1-5ee8-80ac-5ea0d59dd678', email: 'ward5.admin@loadtest.kummayak.gov.np', userId: '820c6048-0440-5743-819b-49096df5af8f' },
    { no: 6, id: '87f18d6b-17b1-5f7c-95a3-6a97f8c48a96', email: 'ward6.admin@loadtest.kummayak.gov.np', userId: '62e1a2a2-86fa-5e57-8f70-0684d2172c8f' },
    { no: 7, id: '6f134ea2-622f-5ba4-8f42-7c6c9587bfeb', email: 'ward7.admin@loadtest.kummayak.gov.np', userId: '4e8870b7-6823-598b-8025-f6f3c9fc8405' },
    { no: 8, id: 'c13e69a3-4cb2-5446-986f-45f100c2527b', email: 'ward8.admin@loadtest.kummayak.gov.np', userId: 'c6868033-5b7e-55cc-95eb-e05ebca30e95' },
    { no: 9, id: 'd04fcc6e-3fa6-5b9c-ad28-b5315ead1ef6', email: 'ward9.admin@loadtest.kummayak.gov.np', userId: '04580a83-0b9e-5b48-a873-daa7e86e4811' },
];

const syncDuration = new Trend('sync_batch_duration', true);

export const options = {
    scenarios: {
        // 9 VUs, 9 iterations total, all released together: one simultaneous
        // sync submission per ward, matching "connectivity restored to the
        // village, all 9 ward devices sync at once".
        nine_ward_sync_burst: {
            executor: 'shared-iterations',
            vus: 9,
            iterations: 9,
            maxDuration: '5m',
        },
    },
    // k6 only reports avg/min/med/max/p(90)/p(95) by default.
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        // A whole-batch request naturally takes longer than a single
        // registration — placeholder, tune after your baseline run.
        'sync_batch_duration': ['p(95)<15000'],
    },
};

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

// k6 has no built-in UUID generator; this doesn't need to be
// cryptographically random, just unique within the run.
function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

function buildOfflineRecord(ward, deviceId, index) {
    const suffix = `${ward.no}-${index}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
    return {
        citizenId: uuidv4(),
        wardId: ward.id,
        nid: `SYNC-${suffix}`,
        citizenshipNo: `SYNCCIT-${suffix}`,
        nameNp: 'सिंक नागरिक',
        nameEn: `Sync Citizen ${suffix}`,
        dob: '1985-06-15',
        sex: 'FEMALE',
        digitalLiteracy: 'NONE',
        hasSmartphone: false,
        consentChannel: 'FIELD',
        registrationChannel: 'MOBILE_APP',
        localRecordId: uuidv4(),
        deviceId,
        versionNumber: 1,
    };
}

export default function (data) {
    const ward = WARDS[__VU - 1];
    const token = data.tokensByWard[ward.no];
    const deviceId = `ward${ward.no}-tablet-01`;

    const records = [];
    for (let i = 0; i < BATCH_SIZE; i++) {
        records.push(buildOfflineRecord(ward, deviceId, i));
    }

    const payload = {
        batchId: uuidv4(),
        wardId: ward.id,
        submittedBy: ward.userId,
        deviceId,
        records,
    };

    const res = http.post(
        `${BASE_URL}/v1/sync/submit`,
        JSON.stringify(payload),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            tags: { name: 'SyncSubmit' },
            timeout: '60s',
        }
    );

    syncDuration.add(res.timings.duration);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'status is SUCCESS': (r) => {
            try {
                return r.json('status') === 'SUCCESS';
            } catch (e) {
                return false;
            }
        },
    }) || console.error(`Ward ${ward.no} sync failed: HTTP ${res.status} ${res.body}`);
}

export function handleSummary(data) {
    return {
        'scripts/load-test/k6/results/sync-burst-test-summary.json': JSON.stringify(data, null, 2),
        stdout: '\n' + textSummaryFallback(data),
    };
}

function textSummaryFallback(data) {
    const m = data.metrics;
    const get = (name, stat) => (m[name] && m[name].values ? m[name].values[stat] : undefined);
    return [
        'SYNC BURST TEST SUMMARY (9 wards, batch size ' + BATCH_SIZE + ')',
        `  requests:        ${get('http_reqs', 'count')}`,
        `  failed rate:     ${get('http_req_failed', 'rate')}`,
        `  batch p50:       ${get('sync_batch_duration', 'med')} ms`,
        `  batch p95:       ${get('sync_batch_duration', 'p(95)')} ms`,
        `  batch max:       ${get('sync_batch_duration', 'max')} ms`,
    ].join('\n');
}