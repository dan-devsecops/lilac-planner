// Lilac Planner — stepped-plateau capacity test (native auth only).
//
// Unlike capacity-test.js (one continuous ramp whose metrics blend the whole
// climb together), this holds FLAT at each VU count in turn — 5, then 10,
// then 20, etc., each as its own scenario — so the summary breaks out
// latency per plateau via the `stage` tag. That's what actually answers
// "how many concurrent users can this handle": look for the first stage
// where p95/max latency jumps, rather than inferring it from one blended
// average.
//
// Usage:
//   k6 run -e BASE_URL=https://your-droplet-domain -e RESULT_LABEL=droplet-steps perf/k6/capacity-steps.js
//
// Tunable via -e: STEPS (default "5,10,20,30,40"), STEP_DURATION (40s),
// PERF_USERNAME/PERF_PASSWORD/PERF_EMAIL. See capacity-test.js's header for
// the auth/rate-limit/TTL notes — they all apply here too (one login in
// setup(), token reused across every stage and VU).
//
// Read the per-stage rows in the printed summary, e.g.:
//   http_req_duration{stage:vu5}...   p(95)=...
//   http_req_duration{stage:vu20}...  p(95)=...
// The stage where p95/max first jumps sharply is your practical ceiling —
// not the stage where it finally 500s or times out.

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8090').replace(/\/+$/, '');
const RESULT_LABEL = __ENV.RESULT_LABEL || 'steps';

const USERNAME = __ENV.PERF_USERNAME || 'perf-test-user';
const PASSWORD = __ENV.PERF_PASSWORD || 'PerfTest123!';
const EMAIL = __ENV.PERF_EMAIL || 'perf-test-user@example.invalid';

const STEPS = (__ENV.STEPS || '5,10,20,30,40').split(',').map((s) => Number(s.trim()));
const STEP_DURATION = __ENV.STEP_DURATION || '40s';
const DATE_SPREAD_DAYS = 500;

function parseDurationSeconds(d) {
  const m = /^(\d+)(s|m)$/.exec(d);
  if (!m) throw new Error(`STEP_DURATION must look like "40s" or "1m", got "${d}"`);
  return m[2] === 'm' ? Number(m[1]) * 60 : Number(m[1]);
}

function buildScenarios() {
  const stepSeconds = parseDurationSeconds(STEP_DURATION);
  const scenarios = {};
  STEPS.forEach((vus, i) => {
    scenarios[`vu${vus}`] = {
      executor: 'constant-vus',
      vus,
      duration: STEP_DURATION,
      startTime: `${i * stepSeconds}s`,
      tags: { stage: `vu${vus}` },
    };
  });
  return scenarios;
}

function buildThresholds() {
  const thresholds = {
    http_req_failed: ['rate<0.05'],
  };
  // k6 only breaks a tag out into its own summary row when a threshold
  // expression references it — it won't do it automatically just because
  // requests carry the tag. These bounds are deliberately unreachable (no
  // real response takes 100s) so they never fail; they exist purely to force
  // each stage's http_req_duration into its own visible row.
  STEPS.forEach((vus) => {
    thresholds[`http_req_duration{stage:vu${vus}}`] = ['p(95)<100000'];
  });
  return thresholds;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: buildThresholds(),
};

export function setup() {
  const headers = { headers: { 'Content-Type': 'application/json' } };

  const registerRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username: USERNAME, email: EMAIL, displayName: 'Perf Test', password: PASSWORD }),
    headers
  );
  if (registerRes.status !== 201 && registerRes.status !== 409) {
    console.warn(`setup: register returned ${registerRes.status}: ${registerRes.body} (continuing)`);
  }

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ login: USERNAME, password: PASSWORD }),
    headers
  );
  if (loginRes.status !== 200) {
    fail(`setup: login failed (${loginRes.status}): ${loginRes.body}. If signup is disabled on this target, pre-create ${USERNAME} and pass matching PERF_USERNAME/PERF_PASSWORD.`);
  }
  const token = loginRes.json('accessToken');
  if (!token) fail('setup: login response had no accessToken');

  return { token };
}

export default function (data) {
  const authHeaders = { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' };
  const date = isoDate(__VU % DATE_SPREAD_DAYS);

  const dayRes = http.get(`${BASE_URL}/api/v1/days/${date}`, {
    headers: authHeaders,
    tags: { group: 'read' },
  });
  check(dayRes, { 'get day 200': (r) => r.status === 200 });

  const addRes = http.post(
    `${BASE_URL}/api/v1/days/${date}/tasks`,
    JSON.stringify({ title: `perf task vu${__VU} iter${__ITER}`, points: 5 }),
    { headers: authHeaders, tags: { group: 'write' } }
  );
  check(addRes, { 'add task 200': (r) => r.status === 200 });

  let taskId;
  if (addRes.status === 200) {
    const day = addRes.json();
    const tasks = day.tasks || [];
    taskId = tasks.length ? tasks[tasks.length - 1].id : undefined;
  }

  if (taskId) {
    const patchRes = http.patch(
      `${BASE_URL}/api/v1/days/${date}/tasks/${taskId}`,
      JSON.stringify({ completed: true }),
      { headers: authHeaders, tags: { group: 'write' } }
    );
    check(patchRes, { 'patch task 200': (r) => r.status === 200 });

    const delRes = http.del(`${BASE_URL}/api/v1/days/${date}/tasks/${taskId}`, null, {
      headers: authHeaders,
      tags: { group: 'write' },
    });
    check(delRes, { 'delete task 200': (r) => r.status === 200 });
  }

  if (__ITER % 5 === 0) {
    const statsRes = http.get(`${BASE_URL}/api/v1/statistics?from=2020-01-01&to=2020-12-31`, {
      headers: authHeaders,
      tags: { group: 'read' },
    });
    check(statsRes, { 'stats 200': (r) => r.status === 200 });
  }

  sleep(1 + Math.random());
}

function isoDate(offsetDays) {
  const d = new Date(Date.UTC(2020, 0, 1));
  d.setUTCDate(d.getUTCDate() + offsetDays);
  return d.toISOString().slice(0, 10);
}

export function handleSummary(data) {
  const out = {};
  out['stdout'] = textSummary(data, { indent: ' ', enableColors: true });
  out[`perf/k6/results/summary-${RESULT_LABEL}.json`] = JSON.stringify(data, null, 2);
  return out;
}
