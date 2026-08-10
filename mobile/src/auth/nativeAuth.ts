// Native (username+password) auth client - the mobile analogue of
// frontend/src/auth/nativeAuth.js, ported to the body-based mobile refresh contract
// (AuthController: {"client":"mobile"} on login, {"refreshToken":"..."}
// on refresh/logout). Tokens live in expo-secure-store, never AsyncStorage.
import * as SecureStore from 'expo-secure-store';
import { API_BASE } from '../config';
import { ApiError, toApiError } from '../api/error';
import { decodeJwtPayload } from './jwt';
import type { AccessTokenResponse, UserDto } from '../api/types';

const BASE = `${API_BASE}/auth`;
const ACCESS_KEY = 'lilac_access_token';
const REFRESH_KEY = 'lilac_refresh_token';

// In-memory cache mirrors SecureStore so request-building stays synchronous, exactly like the
// web client reading localStorage synchronously. Populated by init() at app startup and kept in
// sync by every mutation below.
let accessToken: string | null = null;
let refreshToken: string | null = null;
let initialized = false;

// Notified whenever auth state flips (login/register success, explicit logout, or the API
// client giving up after a failed silent refresh). AuthContext subscribes to drive the
// authenticated/unauthenticated route split declaratively instead of imperative navigation.
const listeners = new Set<() => void>();
export function subscribe(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}
function notify() {
  listeners.forEach((fn) => fn());
}

/** Load persisted tokens into memory. Call once, before rendering any authenticated screen. */
export async function init(): Promise<void> {
  if (initialized) return;
  const [a, r] = await Promise.all([
    SecureStore.getItemAsync(ACCESS_KEY),
    SecureStore.getItemAsync(REFRESH_KEY),
  ]);
  accessToken = a;
  refreshToken = r;
  initialized = true;
}

export function getAccessToken(): string | null {
  return accessToken;
}

// Requiring a decodable payload (not just a non-empty string) means a corrupted/malformed
// access token is treated as logged-out rather than rendering an authenticated shell with a
// garbage identity - see displayName()'s 'User' fallback, which otherwise masks exactly this.
export function isAuthenticated(): boolean {
  return !!accessToken && tokenPayload() !== null;
}

async function storeTokens(tokens: AccessTokenResponse): Promise<void> {
  accessToken = tokens.accessToken;
  const writes = [SecureStore.setItemAsync(ACCESS_KEY, tokens.accessToken)];
  if (tokens.refreshToken) {
    refreshToken = tokens.refreshToken;
    writes.push(SecureStore.setItemAsync(REFRESH_KEY, tokens.refreshToken));
  }
  await Promise.all(writes);
  notify();
}

export async function clearTokens(): Promise<void> {
  accessToken = null;
  refreshToken = null;
  await Promise.all([
    SecureStore.deleteItemAsync(ACCESS_KEY),
    SecureStore.deleteItemAsync(REFRESH_KEY),
  ]);
  notify();
}

export function tokenPayload() {
  return accessToken ? decodeJwtPayload(accessToken) : null;
}

export function displayName(): string {
  const p = tokenPayload();
  return (p?.name as string) || (p?.preferred_username as string) || (p?.sub as string) || 'User';
}

export function roles(): string[] {
  return (tokenPayload()?.roles as string[]) || [];
}

export function isAdmin(): boolean {
  return roles().includes('ADMIN');
}

async function postJson<T>(path: string, body?: unknown, opts: { auth?: boolean } = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (opts.auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const res = await fetch(BASE + path, {
    method: 'POST',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return null as T;
  return res.json();
}

export function register(input: {
  username: string;
  email: string;
  displayName?: string;
  password: string;
}): Promise<UserDto> {
  return postJson('/register', {
    username: input.username,
    email: input.email,
    displayName: input.displayName,
    password: input.password,
  });
}

export async function login(loginId: string, password: string): Promise<AccessTokenResponse> {
  const tokens = await postJson<AccessTokenResponse>('/login', {
    login: loginId,
    password,
    client: 'mobile',
  });
  await storeTokens(tokens);
  return tokens;
}

// In-flight refresh, shared by concurrent callers - the backend rotates refresh tokens (the old
// one is deleted on use), so two parallel refreshes with the same stored token would race: the
// loser gets "Invalid refresh token" and would wrongly look logged out. This mirrors the web
// client's refreshPromise single-flight exactly.
let refreshPromise: Promise<boolean> | null = null;

export function refresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function doRefresh(): Promise<boolean> {
  if (!refreshToken) return false;
  try {
    const tokens = await postJson<AccessTokenResponse>('/refresh', { refreshToken });
    await storeTokens(tokens);
    return true;
  } catch {
    return false;
  }
}

export async function logout(): Promise<void> {
  try {
    if (refreshToken) await postJson('/logout', { refreshToken });
  } catch {
    /* best effort */
  }
  await clearTokens();
}

export function forgotPassword(email: string): Promise<void> {
  return postJson('/forgot-password', { email });
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return postJson('/reset-password', { token, newPassword });
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return postJson('/change-password', { currentPassword, newPassword }, { auth: true });
}

export function adminResetPassword(username: string, newPassword: string): Promise<void> {
  return postJson('/admin/reset-password', { username, newPassword }, { auth: true });
}

export { ApiError };
