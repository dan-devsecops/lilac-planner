import { Platform } from 'react-native';
import Constants, { AppOwnership } from 'expo-constants';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { api } from '../api/client';
import { ensureNotificationPermission } from './alarms';
import * as nativeAuth from '../auth/nativeAuth';

/**
 * Remote Expo push token registration + device-timezone sync - the mobile analogue of
 * frontend/src/push.js. alarms.ts's local scheduled notifications remain the fallback for
 * Expo Go on Android (where remote push, and even importing expo-notifications, is
 * unsupported - see alarms.ts) and for whenever registration below doesn't complete.
 *
 * Same guard as alarms.ts, replicated rather than imported so this module doesn't create a
 * dependency alarms.ts doesn't otherwise need.
 */
const disabledInExpoGoAndroid = Platform.OS === 'android' && Constants.appOwnership === AppOwnership.Expo;

let notificationsModule: typeof import('expo-notifications') | null = null;
let requireAttempted = false;
function getNotifications(): typeof import('expo-notifications') | null {
  if (disabledInExpoGoAndroid) return null;
  if (!requireAttempted) {
    requireAttempted = true;
    notificationsModule = require('expo-notifications');
  }
  return notificationsModule;
}

const TIMEZONE_KEY = 'lilac-push-tz-v1';

export function detectTimezone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

/** Sends the device's IANA timezone to the backend, at most once per detected value. */
export async function syncTimezone(): Promise<void> {
  const timezone = detectTimezone();
  if (!timezone) return;
  const last = await AsyncStorage.getItem(TIMEZONE_KEY).catch(() => null);
  if (last === timezone) return;
  await api.updateTimezone(timezone);
  await AsyncStorage.setItem(TIMEZONE_KEY, timezone).catch(() => {
    /* best effort - will just resend next session */
  });
}

/**
 * EAS project id is required by getExpoPushTokenAsync but this project has none configured yet
 * (no extra.eas.projectId in app.json, no eas.json project link) - treated as "push not set up",
 * not an error, so registration just no-ops until a project id exists.
 */
function projectId(): string | undefined {
  return Constants.expoConfig?.extra?.eas?.projectId;
}

async function registerPushToken(): Promise<void> {
  const Notifications = getNotifications();
  if (!Notifications) return;

  const id = projectId();
  if (!id) return;

  const granted = await ensureNotificationPermission();
  if (!granted) return;

  const { data: token } = await Notifications.getExpoPushTokenAsync({ projectId: id });
  if (!token) return;

  await api.registerPushSubscription({ platform: 'EXPO', token });
}

let started = false;

// Unlike the web app (a full page reload on login/logout naturally resets module state), a
// mobile login/logout never restarts the JS process - it just flips isAuthenticated and
// remounts app/(app)/_layout.tsx. Without this, a device shared across accounts (logout as A,
// login as B without killing the app) would silently skip re-registration forever, leaving the
// Expo push token registered under A instead of B.
nativeAuth.subscribe(() => {
  if (!nativeAuth.isAuthenticated()) started = false;
});

/**
 * Orchestrates push setup once per authenticated session: timezone sync (always, cheap and
 * useful even without push), then - only outside Expo Go on Android and with an EAS project
 * configured - Expo push token registration. Safe to call repeatedly and from anywhere; all
 * failures are swallowed so the caller never needs a try/catch and alarms.ts keeps working
 * regardless.
 */
export async function ensurePushRegistration(): Promise<void> {
  if (started) return;
  started = true;

  try {
    await syncTimezone();
  } catch {
    /* best-effort */
  }

  try {
    await registerPushToken();
  } catch {
    /* best-effort - the local-notification fallback in alarms.ts keeps working */
  }
}
