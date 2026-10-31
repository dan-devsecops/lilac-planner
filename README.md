# 🌸 Lilac Planner

A daily planner web and mobile app with point tracking, animal-sticker rewards, and statistics.
Plan your day, check off tasks, earn points, and collect stickers along the way, all wrapped in
a soft lilac palette.

## Features

- **Daily task planning** with optional scheduled times and alarms
- **Recurring tasks** (daily, weekly, monthly, yearly), auto-materialized across a future horizon
- **Points and animal stickers** awarded as you complete tasks, with configurable thresholds
- **Statistics view** showing point totals across a date range
- **Task rollover** so incomplete tasks move forward instead of getting lost
- **Push notifications** (web push and Expo push) in addition to in-app/local alarm reminders
- **Flexible auth**: Keycloak SSO, native username/password, or no auth for local/dev use
- **Pluggable storage**: Neo4j, Postgres/MariaDB (via JPA), or DynamoDB behind one storage port

## Tech stack

- **Backend**: Spring Boot 3, Java 21
- **Frontend**: React 18, Vite
- **Mobile**: Expo (React Native), TypeScript, Expo Router, TanStack Query
- **Storage**: Neo4j (default), Postgres/MariaDB, or DynamoDB
- **Auth**: Keycloak, native JWT auth, or disabled
- **Infra**: Docker Compose for local dev, Terraform + Ansible + Helm for deployment to Azure,
  DigitalOcean (DOKS or a plain Droplet)

## Quick start

With Docker (no local toolchain needed):

```bash
docker compose up --build
# open http://localhost:5173, login: alice / alice123
```

## Testing

- **Backend unit tests** (`mvn test`, from `backend/`): no live database needed, adapters are
  tested with mocks and JPA runs on H2.
- **Backend integration tests** (`mvn test-compile failsafe:integration-test failsafe:verify`):
  MockMvc against H2, still no live database needed.
- **Storage contract suite** (`-Dit.test='*ContractIT'`): runs the same behavioral contract
  against all three storage adapters, JPA on H2 always, Neo4j and DynamoDB via Testcontainers
  when Docker is available.
- **Full backend verify** (`mvn -B verify`): unit + integration tests plus a JaCoCo line-coverage
  gate of 80%, the same check CI runs.
- **Frontend tests** (`npm test`, from `frontend/`): one-shot Vitest run, `npm run test:watch`
  for watch mode.
- **End-to-end tests** (`npm test`, from `e2e/`): Playwright against a throwaway, isolated Docker
  stack that is spun up and torn down automatically, so it never touches your dev/prod data.
- **Mutation testing** (`mvn -P mutation test-compile pitest:mutationCoverage`, from `backend/`):
  opt-in and slow, not part of CI, report in `target/pit-reports/`.
- **Security scanning**: SAST (Semgrep), SCA (Trivy against dependency manifests), DAST (OWASP
  ZAP baseline against the throwaway e2e stack), and container image scanning (Trivy) all run in
  CI; SonarQube is available for manual, self-hosted scans.

CI runs backend unit and verify, frontend tests, Helm lint, the security scans above, and the
e2e suite on every push and pull request.

## License

AGPL-3.0-or-later, see [LICENSE](LICENSE). Modified versions, including ones run as a network
service, must make their source available to users under the same license.
