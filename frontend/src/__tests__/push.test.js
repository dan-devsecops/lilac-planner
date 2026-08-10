import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../api.js', () => ({
  api: {
    getVapidPublicKey: vi.fn(),
    registerPushSubscription: vi.fn(),
    updateTimezone: vi.fn(),
  },
}));
vi.mock('../notifications.js', () => ({
  ensureNotificationPermission: vi.fn(),
}));

import { api } from '../api.js';
import { ensureNotificationPermission } from '../notifications.js';
import {
  __resetForTests,
  detectTimezone,
  ensurePushRegistration,
  isPushSupported,
  subscriptionToPayload,
  syncTimezone,
  urlBase64ToUint8Array,
} from '../push.js';

function fakeStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    clear: () => map.clear(),
  };
}

beforeEach(() => {
  globalThis.localStorage = fakeStorage();
  vi.clearAllMocks();
  __resetForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('urlBase64ToUint8Array', () => {
  it('decodes a base64url VAPID key into the expected bytes', () => {
    // 'hello' base64url-encoded, no padding
    const result = urlBase64ToUint8Array('aGVsbG8');
    expect(Array.from(result)).toEqual([104, 101, 108, 108, 111]);
  });

  it('handles URL-safe characters (- and _) that plain base64 would use as + and /', () => {
    // bytes [0xfb, 0xff, 0xbf] -> base64 "+/+/" -> base64url "-_-_"
    const result = urlBase64ToUint8Array('-_-_');
    expect(Array.from(result)).toEqual([0xfb, 0xff, 0xbf]);
  });

  it('pads correctly for every valid unpadded-length remainder (2, 3, or 0 mod 4)', () => {
    expect(() => urlBase64ToUint8Array('ab')).not.toThrow();
    expect(() => urlBase64ToUint8Array('abc')).not.toThrow();
    expect(() => urlBase64ToUint8Array('abcd')).not.toThrow();
  });
});

describe('isPushSupported', () => {
  it('returns false when serviceWorker is missing from navigator', () => {
    vi.stubGlobal('navigator', {});
    vi.stubGlobal('window', { PushManager: function () {} });
    expect(isPushSupported()).toBe(false);
  });

  it('returns false when PushManager is missing from window', () => {
    vi.stubGlobal('navigator', { serviceWorker: {} });
    vi.stubGlobal('window', {});
    expect(isPushSupported()).toBe(false);
  });

  it('returns true when both serviceWorker and PushManager are present', () => {
    vi.stubGlobal('navigator', { serviceWorker: {} });
    vi.stubGlobal('window', { PushManager: function () {} });
    expect(isPushSupported()).toBe(true);
  });
});

describe('detectTimezone', () => {
  it('returns the resolved IANA timezone', () => {
    const tz = detectTimezone();
    expect(typeof tz).toBe('string');
    expect(tz.length).toBeGreaterThan(0);
  });

  it('returns null if Intl throws', () => {
    const original = globalThis.Intl.DateTimeFormat;
    globalThis.Intl.DateTimeFormat = () => {
      throw new Error('boom');
    };
    expect(detectTimezone()).toBeNull();
    globalThis.Intl.DateTimeFormat = original;
  });
});

describe('subscriptionToPayload', () => {
  it('shapes a PushSubscription into the backend request body', () => {
    const subscription = {
      toJSON: () => ({
        endpoint: 'https://push.example/abc',
        keys: { p256dh: 'key-p256dh', auth: 'key-auth' },
      }),
    };
    expect(subscriptionToPayload(subscription)).toEqual({
      platform: 'WEB',
      token: 'https://push.example/abc',
      p256dh: 'key-p256dh',
      auth: 'key-auth',
    });
  });

  it('tolerates a missing keys object', () => {
    const subscription = { toJSON: () => ({ endpoint: 'https://push.example/xyz' }) };
    expect(subscriptionToPayload(subscription)).toEqual({
      platform: 'WEB',
      token: 'https://push.example/xyz',
      p256dh: undefined,
      auth: undefined,
    });
  });
});

describe('syncTimezone', () => {
  it('posts the detected timezone once and caches it', async () => {
    api.updateTimezone.mockResolvedValue(null);
    await syncTimezone();
    expect(api.updateTimezone).toHaveBeenCalledTimes(1);
    const [tz] = api.updateTimezone.mock.calls[0];
    expect(tz).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });

  it('does not re-post when the cached timezone is unchanged', async () => {
    api.updateTimezone.mockResolvedValue(null);
    await syncTimezone();
    await syncTimezone();
    expect(api.updateTimezone).toHaveBeenCalledTimes(1);
  });

  it('re-posts when the cached timezone differs from the detected one', async () => {
    api.updateTimezone.mockResolvedValue(null);
    localStorage.setItem('lilac-planner-tz-v1', 'Some/Other');
    await syncTimezone();
    expect(api.updateTimezone).toHaveBeenCalledTimes(1);
  });
});

describe('ensurePushRegistration', () => {
  it('syncs timezone but skips the subscribe flow when push is unsupported', async () => {
    vi.stubGlobal('navigator', {});
    vi.stubGlobal('window', {});
    api.updateTimezone.mockResolvedValue(null);

    await ensurePushRegistration();

    expect(api.updateTimezone).toHaveBeenCalledTimes(1);
    expect(ensureNotificationPermission).not.toHaveBeenCalled();
  });

  it('skips the permission prompt entirely when the backend has no VAPID key configured', async () => {
    vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn() } });
    vi.stubGlobal('window', { PushManager: function () {} });
    api.updateTimezone.mockResolvedValue(null);
    api.getVapidPublicKey.mockResolvedValue({ publicKey: '' });

    await ensurePushRegistration();

    expect(api.getVapidPublicKey).toHaveBeenCalledTimes(1);
    expect(ensureNotificationPermission).not.toHaveBeenCalled();
    expect(navigator.serviceWorker.register).not.toHaveBeenCalled();
  });

  it('swallows a failed VAPID key fetch without throwing', async () => {
    vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn() } });
    vi.stubGlobal('window', { PushManager: function () {} });
    api.updateTimezone.mockResolvedValue(null);
    api.getVapidPublicKey.mockRejectedValue(new Error('network down'));

    await expect(ensurePushRegistration()).resolves.toBeUndefined();
    expect(ensureNotificationPermission).not.toHaveBeenCalled();
  });

  it('skips subscribing when notification permission is refused', async () => {
    vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn() } });
    vi.stubGlobal('window', { PushManager: function () {} });
    api.updateTimezone.mockResolvedValue(null);
    api.getVapidPublicKey.mockResolvedValue({ publicKey: 'aGVsbG8' });
    ensureNotificationPermission.mockResolvedValue(false);

    await ensurePushRegistration();

    expect(ensureNotificationPermission).toHaveBeenCalledTimes(1);
    expect(navigator.serviceWorker.register).not.toHaveBeenCalled();
  });

  it('registers the service worker and subscribes when supported and permitted', async () => {
    const subscription = {
      toJSON: () => ({ endpoint: 'https://push.example/1', keys: { p256dh: 'p', auth: 'a' } }),
    };
    const registration = {
      pushManager: {
        getSubscription: vi.fn().mockResolvedValue(null),
        subscribe: vi.fn().mockResolvedValue(subscription),
      },
    };
    vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn().mockResolvedValue(registration) } });
    vi.stubGlobal('window', { PushManager: function () {} });
    api.updateTimezone.mockResolvedValue(null);
    api.getVapidPublicKey.mockResolvedValue({ publicKey: 'aGVsbG8' });
    api.registerPushSubscription.mockResolvedValue({ id: 'sub-1' });
    ensureNotificationPermission.mockResolvedValue(true);

    await ensurePushRegistration();

    expect(navigator.serviceWorker.register).toHaveBeenCalledWith('/sw.js');
    expect(registration.pushManager.subscribe).toHaveBeenCalledWith(
      expect.objectContaining({ userVisibleOnly: true }),
    );
    expect(api.registerPushSubscription).toHaveBeenCalledWith({
      platform: 'WEB',
      token: 'https://push.example/1',
      p256dh: 'p',
      auth: 'a',
    });
  });

  it('reuses an existing PushManager subscription instead of creating a new one', async () => {
    const subscription = {
      toJSON: () => ({ endpoint: 'https://push.example/existing', keys: { p256dh: 'p', auth: 'a' } }),
    };
    const registration = {
      pushManager: {
        getSubscription: vi.fn().mockResolvedValue(subscription),
        subscribe: vi.fn(),
      },
    };
    vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn().mockResolvedValue(registration) } });
    vi.stubGlobal('window', { PushManager: function () {} });
    api.updateTimezone.mockResolvedValue(null);
    api.getVapidPublicKey.mockResolvedValue({ publicKey: 'aGVsbG8' });
    api.registerPushSubscription.mockResolvedValue({ id: 'sub-1' });
    ensureNotificationPermission.mockResolvedValue(true);

    await ensurePushRegistration();

    expect(registration.pushManager.subscribe).not.toHaveBeenCalled();
    expect(api.registerPushSubscription).toHaveBeenCalledWith(
      expect.objectContaining({ token: 'https://push.example/existing' }),
    );
  });

  it('only runs once per session, even across repeated calls', async () => {
    vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn() } });
    vi.stubGlobal('window', { PushManager: function () {} });
    api.updateTimezone.mockResolvedValue(null);
    api.getVapidPublicKey.mockResolvedValue({ publicKey: 'aGVsbG8' });
    ensureNotificationPermission.mockResolvedValue(false);

    await ensurePushRegistration();
    await ensurePushRegistration();

    expect(api.updateTimezone).toHaveBeenCalledTimes(1);
  });
});
