#!/usr/bin/env bash
# Runs the local, manual SonarQube scan (see docker-compose-sonarqube.yml's header
# comment for one-time setup: starting the stack, creating the three projects, and
# generating a user token) against backend, frontend and/or mobile.
#
#   SONAR_TOKEN=<token> ./sonar-scan.sh [--backend] [--frontend] [--mobile]
#
# With no flags, scans all three. SONAR_HOST_URL defaults to http://localhost:9000.
# Exits 0 (not an error) when SONAR_TOKEN is unset or SonarQube isn't reachable,
# since this is opt-in local tooling - see .githooks/post-commit, which calls this
# in the background after every commit and must never fail a dev's workflow just
# because they haven't set it up.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# Loads SONAR_TOKEN (and optionally SONAR_HOST_URL) from .env.local (gitignored,
# see .env.local.example) so it survives a reboot without a shell-profile export.
# set -a / set +a exports every var sourced in between, since the file has plain
# KEY=value lines with no `export` keyword.
if [ -f "$ROOT/.env.local" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env.local"
  set +a
fi

SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"

if [ -z "${SONAR_TOKEN:-}" ]; then
  echo "sonar-scan: SONAR_TOKEN is not set - skipping (see docker-compose-sonarqube.yml for setup)." >&2
  exit 0
fi

if ! curl -sf --max-time 3 "$SONAR_HOST_URL/api/system/status" >/dev/null 2>&1; then
  echo "sonar-scan: SonarQube not reachable at $SONAR_HOST_URL - skipping (docker compose -f docker-compose-sonarqube.yml up -d)." >&2
  exit 0
fi

scan_backend=0
scan_frontend=0
scan_mobile=0
if [ "$#" -eq 0 ]; then
  scan_backend=1
  scan_frontend=1
  scan_mobile=1
fi
for arg in "$@"; do
  case "$arg" in
    --backend) scan_backend=1 ;;
    --frontend) scan_frontend=1 ;;
    --mobile) scan_mobile=1 ;;
    *) echo "sonar-scan: unknown flag '$arg'" >&2; exit 1 ;;
  esac
done

if [ "$scan_backend" -eq 1 ]; then
  echo "sonar-scan: scanning backend..."
  (cd "$ROOT/backend" && mvn -B verify sonar:sonar \
    -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN")
fi

if [ "$scan_frontend" -eq 1 ]; then
  echo "sonar-scan: scanning frontend..."
  (cd "$ROOT/frontend" && npm run test:coverage && \
    sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN")
fi

if [ "$scan_mobile" -eq 1 ]; then
  echo "sonar-scan: scanning mobile..."
  (cd "$ROOT/mobile" && \
    sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN")
fi

echo "sonar-scan: done - see $SONAR_HOST_URL"
