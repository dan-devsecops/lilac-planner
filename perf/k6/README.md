# Capacity testing

`capacity-test.js` ramps virtual users against the real day/task CRUD loop
(get day → add task → complete task → delete task, plus an occasional
statistics read) and reports latency/error-rate as load climbs, so you can
see roughly how many concurrent users each deployment holds up under before
it degrades.

Native auth only. It self-registers a throwaway test account in `setup()`
(ignoring "already exists"), so no manual account provisioning needed unless
signup is disabled on the target — see the script header for that fallback.

## Prerequisites

- `k6` (installed via `brew install k6`)
- Target reachable over HTTP(S) with `AUTH_PROVIDER=native`

## Run it

One invocation per environment, each with its own `RESULT_LABEL` so the
summary JSON files don't overwrite each other:

```bash
# 1. Localhost (docker-compose stack must be up: docker compose up -d)
k6 run -e BASE_URL=http://localhost:8090 -e RESULT_LABEL=local perf/k6/capacity-test.js

# 2. Droplet production
k6 run -e BASE_URL=https://<your-droplet-domain> -e RESULT_LABEL=droplet perf/k6/capacity-test.js

# 3. Kubernetes (DOKS) production
k6 run -e BASE_URL=https://<your-doks-domain> -e RESULT_LABEL=doks perf/k6/capacity-test.js
```

Useful overrides (all optional, `-e KEY=value`):

| Var | Default | Meaning |
|---|---|---|
| `MAX_VUS` | `50` | peak concurrent virtual users |
| `RAMP_TIME` | `2m` | time to ramp 0 → MAX_VUS |
| `PEAK_TIME` | `3m` | time held at MAX_VUS |
| `RAMP_DOWN` | `30s` | time to ramp back to 0 |
| `PERF_USERNAME` / `PERF_PASSWORD` / `PERF_EMAIL` | `perf-test-user` / `PerfTest123!` / `perf-test-user@example.invalid` | throwaway test account |

Start small (`MAX_VUS=10`) on prod targets before pushing higher — see
**Testing against production** below.

Each run writes `perf/k6/results/summary-<label>.json` plus a console
summary. Once all three are done, ask me to diff them — I'll compare p95
latency and error rate per environment (and can plot it as an artifact if
useful).

## ⚠️ Testing against production

The Droplet and DOKS runs hit your live deployment:

- **It will use real CPU/bandwidth on infrastructure you're paying for**, and
  if anyone else uses the app, they'll feel the load too. Start at a low
  `MAX_VUS` and step up rather than going straight to a big number.
- The in-memory rate limiter (5 logins/min, 3 registers/min per source IP —
  unchanged in prod) is why the script logs in **once** in `setup()` and
  reuses that token for the whole run — see the comment block at the top of
  `capacity-test.js`. Don't add per-VU logins without accounting for this,
  or you'll just be measuring your own rate limiter.
- DOKS may run multiple backend replicas; that rate limiter is per-pod
  in-memory, not shared, which is a real difference from the single-instance
  Droplet worth keeping in mind when comparing results.
- Access tokens default to a 15-minute TTL — keep total run time
  (`RAMP_TIME + PEAK_TIME + RAMP_DOWN`) under that.
- Clean up: the script deletes each task it creates, but the throwaway
  `perf-test-user` account itself persists. Delete it afterward if you'd
  rather not leave test data around (there's no self-delete endpoint —
  removing the user is currently a DB/admin-side operation).
