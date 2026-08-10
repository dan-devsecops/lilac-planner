import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as nativeAuth from '../auth/nativeAuth.js';

// Minimal localStorage stub (vitest runs in node).
function fakeStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    clear: () => map.clear(),
  };
}

// A throwaway JWT with the given payload (signature is irrelevant for display decoding).
function fakeJwt(payload) {
  return `h.${btoa(JSON.stringify(payload))}.s`;
}

function okResponse(body, status = 200) {
  return { ok: true, status, json: async () => body };
}
function errResponse(status, detail) {
  return { ok: false, status, statusText: 'Error', json: async () => ({ detail }) };
}

beforeEach(() => {
  globalThis.localStorage = fakeStorage();
  globalThis.fetch = vi.fn();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('token store', () => {
  it('reports unauthenticated until a token is stored', () => {
    expect(nativeAuth.isAuthenticated()).toBe(false);
    localStorage.setItem('lilac_access_token', 'abc');
    expect(nativeAuth.isAuthenticated()).toBe(true);
    expect(nativeAuth.getAccessToken()).toBe('abc');
  });

  it('clearTokens removes the access token', () => {
    localStorage.setItem('lilac_access_token', 'a');
    nativeAuth.clearTokens();
    expect(nativeAuth.getAccessToken()).toBeNull();
  });

  it('decodes display name and roles from the access token', () => {
    localStorage.setItem('lilac_access_token',
      fakeJwt({ preferred_username: 'alice', name: 'Alice', roles: ['ADMIN', 'USER'] }));
    expect(nativeAuth.displayName()).toBe('Alice');
    expect(nativeAuth.roles()).toEqual(['ADMIN', 'USER']);
    expect(nativeAuth.isAdmin()).toBe(true);
  });

  it('falls back gracefully when there is no token', () => {
    expect(nativeAuth.tokenPayload()).toBeNull();
    expect(nativeAuth.displayName()).toBe('User');
    expect(nativeAuth.isAdmin()).toBe(false);
  });
});

describe('login', () => {
  it('stores the access token (refresh token goes to HttpOnly cookie, not localStorage)', async () => {
    fetch.mockResolvedValue(okResponse({ accessToken: 'AT' }));

    await nativeAuth.login('alice', 'pw');

    expect(nativeAuth.getAccessToken()).toBe('AT');
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/auth/login');
    // 'pw' is a placeholder test fixture, not a real credential.
    expect(JSON.parse(opts.body)).toEqual({ login: 'alice', password: 'pw' });
  });

  it('surfaces the server problem detail on failure', async () => {
    fetch.mockResolvedValue(errResponse(401, 'Invalid username or password'));
    await expect(nativeAuth.login('alice', 'bad')).rejects.toThrow('Invalid username or password');
  });
});

describe('refresh', () => {
  it('stores the new access token and returns true on success', async () => {
    fetch.mockResolvedValue(okResponse({ accessToken: 'AT2' }));

    expect(await nativeAuth.refresh()).toBe(true);
    expect(nativeAuth.getAccessToken()).toBe('AT2');
  });

  it('sends no request body (refresh cookie is forwarded automatically)', async () => {
    fetch.mockResolvedValue(okResponse({ accessToken: 'AT2' }));

    await nativeAuth.refresh();

    const [, opts] = fetch.mock.calls[0];
    expect(opts.body).toBeUndefined();
  });

  it('returns false (without throwing) when refresh is rejected', async () => {
    fetch.mockResolvedValue(errResponse(401, 'Refresh token expired'));
    expect(await nativeAuth.refresh()).toBe(false);
  });

  it('single-flights concurrent calls into one network request', async () => {
    let resolveFetch;
    fetch.mockReturnValue(new Promise((resolve) => { resolveFetch = resolve; }));

    // Both calls start while the first request is still in flight.
    const first = nativeAuth.refresh();
    const second = nativeAuth.refresh();
    resolveFetch(okResponse({ accessToken: 'AT2' }));

    expect(await first).toBe(true);
    expect(await second).toBe(true);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(nativeAuth.getAccessToken()).toBe('AT2');
  });

  it('concurrent callers share a failure result too', async () => {
    fetch.mockResolvedValue(errResponse(401, 'Invalid refresh token'));

    const [first, second] = await Promise.all([nativeAuth.refresh(), nativeAuth.refresh()]);

    expect(first).toBe(false);
    expect(second).toBe(false);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('allows a fresh refresh after the previous one settles', async () => {
    fetch
      .mockResolvedValueOnce(okResponse({ accessToken: 'AT2' }))
      .mockResolvedValueOnce(okResponse({ accessToken: 'AT3' }));

    expect(await nativeAuth.refresh()).toBe(true);
    expect(await nativeAuth.refresh()).toBe(true);

    expect(fetch).toHaveBeenCalledTimes(2);
    // Neither call should send a body - the cookie travels automatically.
    expect(fetch.mock.calls[1][1].body).toBeUndefined();
    expect(nativeAuth.getAccessToken()).toBe('AT3');
  });
});

describe('logout', () => {
  it('clears the access token even when the server call fails', async () => {
    localStorage.setItem('lilac_access_token', 'AT');
    fetch.mockRejectedValue(new Error('network'));

    await nativeAuth.logout();

    expect(nativeAuth.getAccessToken()).toBeNull();
  });

  it('always calls the server on logout (cookie cleared server-side)', async () => {
    fetch.mockResolvedValue({ ok: true, status: 204 });

    await nativeAuth.logout();

    expect(fetch).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({ method: 'POST' }));
  });
});

describe('register / change-password attach context', () => {
  it('register posts the account fields', async () => {
    fetch.mockResolvedValue(okResponse({ id: '1', username: 'bob' }, 201));
    // 'password1' is a placeholder test fixture, not a real credential.
    await nativeAuth.register({ username: 'bob', email: 'b@x.com', displayName: 'Bob', password: 'password1' });
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toBe('/api/v1/auth/register');
    expect(JSON.parse(opts.body)).toMatchObject({ username: 'bob', email: 'b@x.com' });
  });

  it('change-password attaches the bearer token', async () => {
    localStorage.setItem('lilac_access_token', 'AT');
    fetch.mockResolvedValue({ ok: true, status: 204 });
    await nativeAuth.changePassword('old', 'newpass12');
    const [, opts] = fetch.mock.calls[0];
    expect(opts.headers.Authorization).toBe('Bearer AT');
  });
});
