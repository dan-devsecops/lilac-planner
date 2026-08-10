import keycloak from './keycloak.js';
import { isKeycloak, isNative } from './auth/config.js';
import * as nativeAuth from './auth/nativeAuth.js';

const BASE = '/api/v1';
let _stickerCache = null;

async function send(url, options = {}, retried = false) {
  if (isKeycloak) {
    await keycloak.updateToken(30).catch(() => keycloak.login());
  }

  const accessToken = isNative ? nativeAuth.getAccessToken() : null;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (isKeycloak && keycloak.token) {
    headers.Authorization = `Bearer ${keycloak.token}`;
  } else if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const res = await fetch(BASE + url, { ...options, headers });

  // Native: a 401 likely means the short-lived access token expired - try one
  // silent refresh (shared with concurrent callers) and replay. If the refresh
  // failed but the stored token changed meanwhile, someone else refreshed -
  // replay with their token. Only when neither holds: drop tokens, go to login.
  if (res.status === 401 && isNative && !retried) {
    if ((await nativeAuth.refresh()) || nativeAuth.getAccessToken() !== accessToken) {
      return send(url, options, true);
    }
    nativeAuth.clearTokens();
    if (typeof window !== 'undefined') window.location.assign('/login');
    throw new Error('401 Unauthorized');
  }

  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  // Users
  me: () => send('/users/me'),

  // Days + tasks
  getDay: (date, signal) => send(`/days/${date}`, { signal }),
  addTask: (date, payload) =>
    send(`/days/${date}/tasks`, { method: 'POST', body: JSON.stringify(payload) }),
  updateTask: (date, taskId, payload) =>
    send(`/days/${date}/tasks/${taskId}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  deleteTask: (date, taskId) =>
    send(`/days/${date}/tasks/${taskId}`, { method: 'DELETE' }),
  reorderTasks: (date, orderedIds) =>
    send(`/days/${date}/tasks/reorder`, { method: 'PUT', body: JSON.stringify(orderedIds) }),

  // Stickers + stats
  getStickers: () =>
    _stickerCache
      ? Promise.resolve(_stickerCache)
      : send('/stickers').then((list) => { _stickerCache = list; return list; }),
  getStats: (from, to) => send(`/statistics?from=${from}&to=${to}`),

  // Push notifications
  getVapidPublicKey: () => send('/push/vapid-public-key'),
  registerPushSubscription: (payload) =>
    send('/me/push-subscriptions', { method: 'POST', body: JSON.stringify(payload) }),
  listPushSubscriptions: () => send('/me/push-subscriptions'),
  deletePushSubscription: (id) =>
    send(`/me/push-subscriptions/${id}`, { method: 'DELETE' }),
  updateTimezone: (timezone) =>
    send('/me/timezone', { method: 'PATCH', body: JSON.stringify({ timezone }) }),
};
