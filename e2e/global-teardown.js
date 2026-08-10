import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const PROJECT = process.env.E2E_PROJECT ?? 'lilac-planner-e2e';
const DB = process.env.PLANNER_DB ?? 'mariadb';
// Same profile as setup so the profiled DB container is included in the teardown.
const COMPOSE = `docker compose -p ${PROJECT} -f docker-compose-e2e.yml --profile ${DB}`;

/**
 * Tear the throwaway stack down, removing its volumes (-v). Because the project is
 * dedicated to e2e, this only deletes e2e's own data - never your dev/prod stack.
 *
 * Skipped when E2E_EXTERNAL_STACK=1 (nothing was started) or E2E_KEEP_STACK=1
 * (leave it up for debugging a failed run).
 */
export default function globalTeardown() {
  if (process.env.E2E_EXTERNAL_STACK === '1' || process.env.E2E_KEEP_STACK === '1') return;
  execSync(`${COMPOSE} down -v --remove-orphans`, { cwd: ROOT, stdio: 'inherit', env: { ...process.env, PLANNER_DB: DB } });
}
