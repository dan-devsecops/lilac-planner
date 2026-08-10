import { test, expect } from '@playwright/test';

// Each test here manages its own login - don't load the project's Keycloak storage state.
test.use({ storageState: { cookies: [], origins: [] } });

/**
 * Browser-level proof that the refresh_token cookie is HttpOnly: the cookie is
 * present in the browser's cookie store (readable via CDP) but invisible to
 * JavaScript (document.cookie).
 *
 * Skips automatically when the running stack does not use native auth
 * (AUTH_PROVIDER != native). To run against a local native-auth stack:
 *
 *   docker compose -f docker-compose.yml -f docker-compose-mariadb.yml \
 *     -f docker-compose-native.yml up -d
 *   PLANNER_JWT_SECRET=$(openssl rand -base64 48) \
 *   BASE_URL=http://localhost:5173 E2E_EXTERNAL_STACK=1 \
 *     npm test -- native-auth-cookie
 */
test.describe('native auth - HttpOnly cookie security', () => {
  let testUser;

  test.beforeEach(async ({ page }) => {
    await page.goto('/');

    // AuthController only registers when AUTH_PROVIDER=native; other modes return 404.
    const probeStatus = await page.evaluate(async () => {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      });
      return res.status;
    });
    test.skip(probeStatus === 404, 'Requires native auth (AUTH_PROVIDER=native)');

    // Unique user per test so tests are fully independent.
    const ts = Date.now();
    testUser = { username: `e2e_${ts}`, password: 'E2ePass1!' };
    await page.evaluate(async ({ username, password }) => {
      const res = await fetch('/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          email: `${username}@e2e.test`,
          displayName: username,
          password,
        }),
      });
      if (!res.ok) throw new Error(`register ${res.status}`);
    }, testUser);
  });

  test('refresh_token is hidden from document.cookie but present in browser storage', async ({ page }) => {
    await page.evaluate(async ({ username, password }) => {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login: username, password }),
      });
      if (!res.ok) throw new Error(`login ${res.status}`);
    }, testUser);

    // JavaScript must not be able to read the refresh token.
    const jsCookies = await page.evaluate(() => document.cookie);
    expect(jsCookies).not.toContain('refresh_token');

    // The cookie IS in the browser - Playwright reads it via CDP (not via JS).
    const allCookies = await page.context().cookies();
    const refreshCookie = allCookies.find(c => c.name === 'refresh_token');
    expect(refreshCookie, 'refresh_token must be set after login').toBeDefined();
    expect(refreshCookie.httpOnly).toBe(true);
    expect(refreshCookie.sameSite).toBe('Strict');
    expect(refreshCookie.value).not.toBe('');
  });

  test('access token is not set as a cookie (stays in memory only)', async ({ page }) => {
    const loginBody = await page.evaluate(async ({ username, password }) => {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login: username, password }),
      });
      if (!res.ok) throw new Error(`login ${res.status}`);
      return res.json();
    }, testUser);

    // Access token comes back in the response body, not a cookie.
    expect(loginBody.accessToken).toBeTruthy();

    // No access-token cookie exists in the browser.
    const allCookies = await page.context().cookies();
    expect(allCookies.find(c => c.name === 'access_token')).toBeUndefined();
  });

  test('refresh_token cookie is cleared from the browser after logout', async ({ page }) => {
    // Log in to establish the cookie.
    await page.evaluate(async ({ username, password }) => {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login: username, password }),
      });
      if (!res.ok) throw new Error(`login ${res.status}`);
    }, testUser);

    const cookiesAfterLogin = await page.context().cookies();
    expect(cookiesAfterLogin.some(c => c.name === 'refresh_token' && c.value !== '')).toBe(true);

    // Logout - server sets Max-Age=0, instructing the browser to delete the cookie.
    await page.evaluate(async () => {
      const res = await fetch('/api/v1/auth/logout', { method: 'POST' });
      if (!res.ok) throw new Error(`logout ${res.status}`);
    });

    const cookiesAfterLogout = await page.context().cookies();
    const remaining = cookiesAfterLogout.find(c => c.name === 'refresh_token');
    // Cookie is either removed entirely or has an empty value.
    expect(remaining?.value ?? '').toBe('');

    // Invariant: still not visible to JS post-logout.
    const jsCookies = await page.evaluate(() => document.cookie);
    expect(jsCookies).not.toContain('refresh_token');
  });

  test('document.cookie remains clean even after a failed login attempt', async ({ page }) => {
    await page.evaluate(async ({ username }) => {
      // Wrong password - server returns 401, no Set-Cookie issued.
      await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login: username, password: 'wrong-password' }),
      });
    }, testUser);

    const jsCookies = await page.evaluate(() => document.cookie);
    expect(jsCookies).not.toContain('refresh_token');

    const allCookies = await page.context().cookies();
    expect(allCookies.find(c => c.name === 'refresh_token')).toBeUndefined();
  });
});
