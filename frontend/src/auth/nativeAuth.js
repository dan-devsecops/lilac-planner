// Native (username+password) auth client. Talks to /api/v1/auth/**. Only used when
// authProvider === 'native'. The access token is kept in localStorage; the refresh
// token lives in an HttpOnly cookie managed by the server (invisible to JS).

const ACCESS_KEY = 'lilac_access_token';
const BASE = '/api/v1/auth';

export function getAccessToken() {
  return localStorage.getItem(ACCESS_KEY);
}

export function isAuthenticated() {
  return !!getAccessToken();
}

function storeTokens(tokens) {
  if (tokens?.accessToken) localStorage.setItem(ACCESS_KEY, tokens.accessToken);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY);
}

/** Decode the JWT payload (no verification - display only). */
export function tokenPayload() {
  const token = getAccessToken();
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function displayName() {
  const p = tokenPayload();
  return p?.name || p?.preferred_username || p?.sub || 'User';
}

export function roles() {
  return tokenPayload()?.roles || [];
}

export function isAdmin() {
  return roles().includes('ADMIN');
}

async function postJson(path, body, { auth = false } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  const init = { method: 'POST', headers };
  if (body !== undefined) init.body = JSON.stringify(body);
  const res = await fetch(BASE + path, init);
  if (!res.ok) {
    let detail = `${res.status} ${res.statusText}`;
    try {
      const problem = await res.json();
      if (problem?.detail) detail = problem.detail;
    } catch { /* keep status text */ }
    throw new Error(detail);
  }
  return res.status === 204 ? null : res.json();
}

export function register({ username, email, displayName: dn, password }) {
  return postJson('/register', { username, email, displayName: dn, password });
}

export async function login(loginId, password) {
  const tokens = await postJson('/login', { login: loginId, password });
  storeTokens(tokens);
  return tokens;
}

// In-flight refresh, shared by concurrent callers. The backend ROTATES refresh
// tokens (the old one is deleted on use), so two parallel refresh calls with the
// same stored token would race: the loser gets "Invalid refresh token" and would
// wrongly look logged-out. Single-flighting guarantees one network call per expiry.
let refreshPromise = null;

/** Try once to exchange the stored refresh token for a fresh pair. Returns success.
 *  Concurrent calls share a single request and resolve with the same result. */
export function refresh() {
  refreshPromise ??= doRefresh().finally(() => { refreshPromise = null; });
  return refreshPromise;
}

async function doRefresh() {
  try {
    // The refresh cookie is sent automatically; no body needed.
    const tokens = await postJson('/refresh');
    storeTokens(tokens);
    return true;
  } catch {
    return false;
  }
}

export async function logout() {
  try {
    // The server reads the refresh cookie and clears it via Set-Cookie.
    await postJson('/logout');
  } catch {
    /* best effort */
  }
  clearTokens();
}

export function forgotPassword(email) {
  return postJson('/forgot-password', { email });
}

export function resetPassword(token, newPassword) {
  return postJson('/reset-password', { token, newPassword });
}

export function changePassword(currentPassword, newPassword) {
  return postJson('/change-password', { currentPassword, newPassword }, { auth: true });
}

export function adminResetPassword(username, newPassword) {
  return postJson('/admin/reset-password', { username, newPassword }, { auth: true });
}
