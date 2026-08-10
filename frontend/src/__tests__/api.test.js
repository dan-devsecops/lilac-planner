import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Force native-auth mode and stub out the Keycloak adapter (its module constructs
// a Keycloak instance at import time, which we never want in tests).
vi.mock('../auth/config.js', () => ({
  authProvider: 'native',
  isKeycloak: false,
  isNative: true,
  isNone: false,
}));
vi.mock('../keycloak.js', () => ({ default: {} }));

import { api } from '../api.js';
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

function okResponse(body, status = 200) {
  return { ok: true, status, json: async () => body };
}
function unauthorized() {
  return { ok: false, status: 401, statusText: 'Unauthorized', json: async () => ({}), text: async () => '' };
}

const refreshCalls = () => fetch.mock.calls.filter(([url]) => url === '/api/v1/auth/refresh');

beforeEach(() => {
  globalThis.localStorage = fakeStorage();
  globalThis.fetch = vi.fn();
  localStorage.setItem('lilac_access_token', 'expired-AT');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('api.getDay() - AbortController / signal threading', () => {
  it('passes the signal to fetch so the request can be cancelled', async () => {
    fetch.mockImplementation(async () => okResponse({ date: '2026-06-13', tasks: [] }));

    const controller = new AbortController();
    await api.getDay('2026-06-13', controller.signal);

    const [, opts] = fetch.mock.calls.find(([url]) => url === '/api/v1/days/2026-06-13');
    expect(opts.signal).toBe(controller.signal);
  });

  it('rejects with an AbortError when the signal is already aborted before the call', async () => {
    // Modern fetch implementations reject immediately if the signal is already aborted.
    fetch.mockImplementation((_url, opts) => {
      if (opts?.signal?.aborted) {
        const err = new DOMException('The operation was aborted.', 'AbortError');
        return Promise.reject(err);
      }
      return Promise.resolve(okResponse({}));
    });

    const controller = new AbortController();
    controller.abort();
    await expect(api.getDay('2026-06-13', controller.signal)).rejects.toMatchObject({
      name: 'AbortError',
    });
  });
});

describe('send() 401 handling (native auth)', () => {
  it('refreshes once and replays the request with the new token', async () => {
    fetch.mockImplementation(async (url, opts) => {
      if (url === '/api/v1/auth/refresh') {
        return okResponse({ accessToken: 'AT2', refreshToken: 'RT2' });
      }
      return opts.headers.Authorization === 'Bearer AT2'
        ? okResponse({ username: 'alice' })
        : unauthorized();
    });

    expect(await api.me()).toEqual({ username: 'alice' });
    expect(refreshCalls()).toHaveLength(1);
  });

  it('concurrent 401s share one refresh and do not wipe the fresh tokens', async () => {
    fetch.mockImplementation(async (url, opts) => {
      if (url === '/api/v1/auth/refresh') {
        return okResponse({ accessToken: 'AT2', refreshToken: 'RT2' });
      }
      return opts.headers.Authorization === 'Bearer AT2'
        ? okResponse({ ok: true })
        : unauthorized();
    });

    // Both fire with the expired token; both 401; both hit the refresh path.
    const [a, b] = await Promise.all([api.me(), api.getDay('2026-06-12')]);

    expect(a).toEqual({ ok: true });
    expect(b).toEqual({ ok: true });
    // The rotating refresh token was sent exactly once...
    expect(refreshCalls()).toHaveLength(1);
    // ...and the fresh access token survived (no clearTokens from the "loser").
    expect(nativeAuth.getAccessToken()).toBe('AT2');
  });

  it('retries with the new token when refresh fails but another caller already refreshed', async () => {
    fetch.mockImplementation(async (url, opts) => {
      if (url === '/api/v1/auth/refresh') {
        // Simulate losing the rotation race (e.g. to another tab): our refresh
        // token is rejected, but a fresh access token landed in storage.
        localStorage.setItem('lilac_access_token', 'AT-fresh');
        return unauthorized();
      }
      return opts.headers.Authorization === 'Bearer AT-fresh'
        ? okResponse({ ok: true })
        : unauthorized();
    });

    expect(await api.me()).toEqual({ ok: true });
    // The winner's tokens are untouched - no forced logout.
    expect(nativeAuth.getAccessToken()).toBe('AT-fresh');
  });

  it('clears tokens and rejects when refresh truly fails', async () => {
    fetch.mockImplementation(async () => unauthorized());

    await expect(api.me()).rejects.toThrow('401 Unauthorized');
    expect(nativeAuth.getAccessToken()).toBeNull();
  });

  it('never retries more than once (no refresh loop on persistent 401)', async () => {
    fetch.mockImplementation(async (url) =>
      url === '/api/v1/auth/refresh'
        ? okResponse({ accessToken: 'AT2', refreshToken: 'RT2' })
        : unauthorized());

    await expect(api.me()).rejects.toThrow('401');
    expect(refreshCalls()).toHaveLength(1);
    expect(fetch.mock.calls.filter(([url]) => url === '/api/v1/users/me')).toHaveLength(2);
  });
});
