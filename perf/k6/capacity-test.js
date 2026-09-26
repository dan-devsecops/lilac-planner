// Lilac Planner — capacity/ramp test (native auth only).
//
// Ramps virtual users up to MAX_VUS and holds, exercising the core day/task CRUD
// loop (the app's actual hot path) so you can see where latency/error-rate starts
// to degrade. Run the same script against each environment with a different
// RESULT_LABEL and diff the summary-*.json files it writes.
//
// Usage:
//   k6 run -e BASE_URL=http://localhost:8090 -e RESULT_LABEL=local perf/k6/capacity-test.js
//   k6 run -e BASE_URL=https://your-droplet-domain -e RESULT_LABEL=droplet perf/k6/capacity-test.js
//   k6 run -e BASE_URL=https://your-doks-domain -e RESULT_LABEL=doks perf/k6/capacity-test.js
//
// Tunable via -e: MAX_VUS (default 50), RAMP_TIME (2m), PEAK_TIME (3m),
// RAMP_DOWN (30s), PERF_USERNAME/PERF_PASSWORD/PERF_EMAIL (test account).
//
// Requires: AUTH_PROVIDER=native on the target and PLANNER_SIGNUP_ENABLED not
// disabled (setup() self-registers the test user, ignoring "already exists").
// If signup is disabled, pre-create the account yourself and just pass its
// PERF_USERNAME/PERF_PASSWORD.
//
// NOTE on rate limiting: /auth/login is capped at 5/min per source IP
// (planner.rate-limit.login-per-minute) and /auth/register at 3/min — both
// defaults, unchanged in prod. That's why this script logs in exactly ONCE in
// setup() and reuses the access token across every VU for the run, instead of
// each VU logging in itself (which would immediately 429 against your own
// client IP long before it tells you anything about server capacity). Access
// tokens default to a 15-minute TTL (PLANNER_ACCESS_TTL) — keep total run time
// under that, or extend the TTL on the target for the test.

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8090').replace(/\/+$/, '');
const RESULT_LABEL = __ENV.RESULT_LABEL || 'local';

const USERNAME = __ENV.PERF_USERNAME || 'perf-test-user';
const PASSWORD = __ENV.PERF_PASSWORD || 'PerfTest123!';
const EMAIL = __ENV.PERF_EMAIL || 'perf-test-user@example.invalid';

const MAX_VUS = Number(__ENV.MAX_VUS || 50);
const RAMP_TIME = __ENV.RAMP_TIME || '2m';
const PEAK_TIME = __ENV.PEAK_TIME || '3m';
const RAMP_DOWN = __ENV.RAMP_DOWN || '30s';

// Spread writes across this many distinct future dates so VUs don't all
// contend for the same (userId, date) day document — that would measure lock
// contention, not raw throughput. Each VU sticks to one date all run.
const DATE_SPREAD_DAYS = 500;

export const options = {
  scenarios: {
    capacity: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_TIME, target: MAX_VUS },
        { duration: PEAK_TIME, target: MAX_VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{group:read}': ['p(95)<800'],
    'http_req_duration{group:write}': ['p(95)<1500'],
  },
};

function isoDate(offsetDays) {
  const d = new Date(Date.UTC(2020, 0, 1)); // fixed epoch — Date.now() is off-limits in k6 init/VU code paths that need determinism, and it doesn't matter which real date we use
  d.setUTCDate(d.getUTCDate() + offsetDays);
  return d.toISOString().slice(0, 10);
}

export function setup() {
  const headers = { headers: { 'Content-Type': 'application/json' } };

  const registerRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username: USERNAME, email: EMAIL, displayName: 'Perf Test', password: PASSWORD }),
    headers
  );
  if (registerRes.status !== 201 && registerRes.status !== 409) {
    console.warn(`setup: register returned ${registerRes.status}: ${registerRes.body} (continuing — user may already exist under a different check)`);
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

  // READ: load the day
  const dayRes = http.get(`${BASE_URL}/api/v1/days/${date}`, {
    headers: authHeaders,
    tags: { group: 'read' },
  });
  check(dayRes, { 'get day 200': (r) => r.status === 200 });

  // WRITE: add a task
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
    // WRITE: complete it
    const patchRes = http.patch(
      `${BASE_URL}/api/v1/days/${date}/tasks/${taskId}`,
      JSON.stringify({ completed: true }),
      { headers: authHeaders, tags: { group: 'write' } }
    );
    check(patchRes, { 'patch task 200': (r) => r.status === 200 });

    // WRITE: clean it back up so the day doc doesn't grow unbounded over the run
    const delRes = http.del(`${BASE_URL}/api/v1/days/${date}/tasks/${taskId}`, null, {
      headers: authHeaders,
      tags: { group: 'write' },
    });
    check(delRes, { 'delete task 200': (r) => r.status === 200 });
  }

  // occasional READ: statistics range
  if (__ITER % 5 === 0) {
    const statsRes = http.get(`${BASE_URL}/api/v1/statistics?from=2020-01-01&to=2020-12-31`, {
      headers: authHeaders,
      tags: { group: 'read' },
    });
    check(statsRes, { 'stats 200': (r) => r.status === 200 });
  }

  sleep(1 + Math.random());
}

export function handleSummary(data) {
  const out = {};
  out[`stdout`] = textSummary(data, { indent: ' ', enableColors: true });
  out[`perf/k6/results/summary-${RESULT_LABEL}.json`] = JSON.stringify(data, null, 2);
  return out;
}
