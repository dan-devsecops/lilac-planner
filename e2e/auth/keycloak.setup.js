import { test as setup } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const STATE_FILE = path.join(__dirname, '.storage-state.json');

setup('authenticate', async ({ page }) => {
  await page.goto('/');

  // Wait for whichever destination arrives: the app (no-SSO) or Keycloak login (SSO).
  // The Keycloak JS adapter redirects after the load event, so we can't rely on
  // the URL right after page.goto().
  await page.waitForURL(/day\/\d{4}|localhost:8080/, { timeout: 15_000 });

  if (page.url().includes('localhost:8080')) {
    // SSO mode - log in via Keycloak and capture the session.
    await page.fill('#username', process.env.KC_USER ?? 'alice');
    await page.fill('#password', process.env.KC_PASSWORD ?? 'alice123');
    await page.click('[name="login"]');
    await page.waitForURL(/localhost:5173\/day\//);
  }

  // Both modes end here: either we landed on /day/ directly or after login.
  await page.context().storageState({ path: STATE_FILE });
});
