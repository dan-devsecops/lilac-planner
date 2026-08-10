// Planner API client - the mobile analogue of frontend/src/api.js. Owns all HTTP concerns:
// base URL, Bearer attachment, single retry via refresh on 401, and problem+json parsing.
import { API_BASE } from '../config';
import * as nativeAuth from '../auth/nativeAuth';
import { ApiError, toApiError } from './error';
import type {
  AppVersionInfo,
  DayDto,
  PushSubscriptionDto,
  PushSubscriptionRequest,
  Sticker,
  StatPointDto,
  TaskRequest,
  UserDto,
} from './types';

let stickerCache: Sticker[] | null = null;

async function send<T>(
  path: string,
  options: RequestInit = {},
  retried = false
): Promise<T> {
  const accessToken = nativeAuth.getAccessToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const res = await fetch(API_BASE + path, { ...options, headers });

  // A 401 most likely means the short-lived access token expired - try one silent refresh
  // (shared with concurrent callers) and replay. Mirrors frontend/src/api.js exactly, including
  // the race-safety: if our refresh lost the rotation race but another caller's refresh landed
  // meanwhile, replay with their token instead of forcing a logout.
  if (res.status === 401 && !retried) {
    const refreshed = await nativeAuth.refresh();
    if (refreshed || nativeAuth.getAccessToken() !== accessToken) {
      return send<T>(path, options, true);
    }
    await nativeAuth.clearTokens();
    throw new ApiError(401, 'Session expired');
  }

  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return null as T;
  return res.json();
}

export const api = {
  me: (): Promise<UserDto> => send('/users/me'),

  getDay: (date: string, signal?: AbortSignal): Promise<DayDto> => send(`/days/${date}`, { signal }),
  addTask: (date: string, payload: TaskRequest): Promise<DayDto> =>
    send(`/days/${date}/tasks`, { method: 'POST', body: JSON.stringify(payload) }),
  updateTask: (date: string, taskId: string, payload: TaskRequest): Promise<DayDto> =>
    send(`/days/${date}/tasks/${taskId}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  deleteTask: (date: string, taskId: string): Promise<DayDto> =>
    send(`/days/${date}/tasks/${taskId}`, { method: 'DELETE' }),
  reorderTasks: (date: string, orderedIds: string[]): Promise<DayDto> =>
    send(`/days/${date}/tasks/reorder`, { method: 'PUT', body: JSON.stringify(orderedIds) }),

  getStickers: (): Promise<Sticker[]> =>
    stickerCache
      ? Promise.resolve(stickerCache)
      : send<Sticker[]>('/stickers').then((list) => {
          stickerCache = list;
          return list;
        }),
  getStats: (from: string, to: string): Promise<StatPointDto[]> =>
    send(`/statistics?from=${from}&to=${to}`),

  getMeta: (signal?: AbortSignal): Promise<AppVersionInfo> => send('/meta', { signal }),

  registerPushSubscription: (payload: PushSubscriptionRequest): Promise<PushSubscriptionDto> =>
    send('/me/push-subscriptions', { method: 'POST', body: JSON.stringify(payload) }),
  listPushSubscriptions: (): Promise<PushSubscriptionDto[]> => send('/me/push-subscriptions'),
  deletePushSubscription: (id: string): Promise<void> =>
    send(`/me/push-subscriptions/${id}`, { method: 'DELETE' }),
  updateTimezone: (timezone: string): Promise<void> =>
    send('/me/timezone', { method: 'PATCH', body: JSON.stringify({ timezone }) }),
};

export { ApiError };
