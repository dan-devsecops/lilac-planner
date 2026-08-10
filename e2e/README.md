# End-to-end tests (Playwright)

These tests drive the real app in a browser against a **fully isolated, throwaway
stack** that the suite builds, starts and tears down itself. There is no shared
database and no `/api/test/reset` endpoint - so running e2e can never touch your
dev or production data.

## Prerequisites

- **Docker** (the suite runs `docker compose`)
- **Node 20**

## Run

```bash
cd e2e
npm install                 # first run only
npx playwright install chromium   # first run only - downloads the browser
npm test                    # build + start the stack, run all specs, tear it down
```

That single `npm test`:

1. **global-setup** runs `docker compose -p lilac-planner-e2e -f docker-compose-e2e.yml --profile <db> up --build -d`
   and waits for the backend to report healthy.
2. Playwright runs the specs in `tests/` against the stack.
3. **global-teardown** runs `docker compose ... down -v`, removing the stack **and its volumes**.

### Choosing the storage backend

The browser flows are DB-agnostic, so by default they run on **MariaDB** (adapter
parity itself is covered by the backend `*ContractIT` suite). To run the same flows
against another backend, set `PLANNER_DB` - each is a Compose **profile**, so only
that one database starts:

```bash
PLANNER_DB=mariadb  npm test   # default
PLANNER_DB=postgres npm test
PLANNER_DB=neo4j    npm test
PLANNER_DB=dynamodb npm test
```

### Why it's safe / isolated

`docker-compose-e2e.yml` is a self-contained, no-SSO stack with:

- its own compose **project** (`lilac-planner-e2e`) → its own network and **anonymous DB volume**, deleted on `down -v`;
- distinct **container names** (`lilac-e2e-*`) and **host ports** - frontend **5174**, backend **8091** -
  so it runs happily alongside a dev stack on 5173/8090 without clashing.

## Useful flags

| Variable | Effect |
|---|---|
| `E2E_EXTERNAL_STACK=1` | Don't start/stop Docker - run against an already-running stack (you manage it). |
| `E2E_KEEP_STACK=1` | Run the stack up as normal but **don't** tear it down - handy for debugging a failure. |
| `BASE_URL` | Override the frontend URL (default `http://localhost:5174`). |
| `E2E_HEALTH_URL` | Override the readiness probe (default `http://localhost:8091/actuator/health`). |
| `E2E_PROJECT` | Override the compose project name (default `lilac-planner-e2e`). |
| `PLANNER_DB` | Storage backend / Compose profile: `mariadb` (default) `\| postgres \| neo4j \| dynamodb`. |

## Debugging

```bash
npx playwright test --headed         # watch it run in a real browser
npm run test:ui                      # interactive Playwright UI
npm run report                       # open the last HTML report
E2E_KEEP_STACK=1 npm test            # leave the stack up afterward, then:
docker compose -p lilac-planner-e2e -f docker-compose-e2e.yml logs backend
docker compose -p lilac-planner-e2e -f docker-compose-e2e.yml down -v   # clean up when done
```

## CI

The `e2e` job in `.github/workflows/ci.yml` just installs Playwright and runs `npm test` -
the suite owns the stack lifecycle, so CI needs no separate "start the stack" step.
