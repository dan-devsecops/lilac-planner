import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { request } from '@playwright/test';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const PROJECT = process.env.E2E_PROJECT ?? 'lilac-planner-e2e';
// Pick the storage backend: mariadb (default) | postgres | neo4j | dynamodb.
// Each is a Compose profile in docker-compose-e2e.yml, so only the chosen DB starts.
const DB = process.env.PLANNER_DB ?? 'mariadb';
const COMPOSE = `docker compose --project-name ${PROJECT} -f docker-compose-e2e.yml --profile ${DB}`;
const HEALTH_URL = process.env.E2E_HEALTH_URL ?? 'http://localhost:8091/actuator/health';
const FRONTEND_URL = process.env.BASE_URL ?? 'http://localhost:5174';

/**
 * Bring up an isolated, throwaway stack for the run against PLANNER_DB. The dedicated
 * compose project name gives it its own volumes, which global-teardown removes - so
 * e2e never shares a database with, or wipes, your dev/prod data. No /api/test/reset.
 *
 * Set E2E_EXTERNAL_STACK=1 to run against an already-running stack instead (the
 * suite then only waits for health and tears nothing down).
 */
export default async function globalSetup() {
  if (process.env.E2E_EXTERNAL_STACK !== '1') {
    execSync(`${COMPOSE} up --build -d`, {
      cwd: ROOT,
      stdio: 'inherit',
      env: { ...process.env, PLANNER_DB: DB },
    });
  }
  await waitForHealth();
  await waitForFrontend();
}

async function waitForHealth() {
  const deadline = Date.now() + 180_000;
  const api = await request.newContext();
  try {
    for (;;) {
      try {
        const res = await api.get(HEALTH_URL);
        if (res.ok() && (await res.json()).status === 'UP') return;
      } catch {
        /* not up yet */
      }
      if (Date.now() > deadline) {
        throw new Error(`Backend never became healthy at ${HEALTH_URL} within 180s.`);
      }
      await new Promise((r) => setTimeout(r, 2000));
    }
  } finally {
    await api.dispose();
  }
}

async function waitForFrontend() {
  // Require 3 consecutive successful responses before handing off to Playwright.
  // A single 200 is not enough under heavy runner load: the server can accept the
  // health-check connection and then stall or reset the very next request from the
  // browser, producing ERR_CONNECTION_RESET / ERR_CONNECTION_REFUSED in the tests.
  const REQUIRED_CONSECUTIVE = 3;
  const deadline = Date.now() + 90_000;
  const api = await request.newContext();
  try {
    let consecutive = 0;
    for (;;) {
      try {
        const res = await api.get(FRONTEND_URL);
        if (res.ok()) {
          consecutive++;
          if (consecutive >= REQUIRED_CONSECUTIVE) return;
        } else {
          consecutive = 0;
        }
      } catch {
        consecutive = 0;
      }
      if (Date.now() > deadline) {
        throw new Error(`Frontend never became reachable at ${FRONTEND_URL} within 90s.`);
      }
      await new Promise((r) => setTimeout(r, 1000));
    }
  } finally {
    await api.dispose();
  }
}
