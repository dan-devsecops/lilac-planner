import { api } from './api.js';
import { ensureNotificationPermission } from './notifications.js';

const SERVICE_WORKER_URL = '/sw.js';
const TIMEZONE_KEY = 'lilac-planner-tz-v1';

let started = false;

/**
 * Web Push registration + browser-timezone sync, the "real" push channel.
 * notifications.js's in-page poller is the fallback for browsers/contexts
 * where this isn't supported or permission is refused - it is untouched.
 */
export function isPushSupported() {
  return (
    typeof navigator !== 'undefined' &&
    'serviceWorker' in navigator &&
    typeof window !== 'undefined' &&
    'PushManager' in window
  );
}

/**
 * Converts a URL-safe base64 VAPID key into the Uint8Array PushManager.subscribe expects.
 * Standard MDN conversion helper - see
 * https://developer.mozilla.org/en-US/docs/Web/API/Push_API/Best_Practices
 */
export function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i++) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

export function detectTimezone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

/** Sends the browser's IANA timezone to the backend, at most once per detected value. */
export async function syncTimezone() {
  const timezone = detectTimezone();
  if (!timezone) return;
  let last = null;
  try {
    last = localStorage.getItem(TIMEZONE_KEY);
  } catch {
    /* private mode / disabled - fall through and send anyway */
  }
  if (last === timezone) return;
  await api.updateTimezone(timezone);
  try {
    localStorage.setItem(TIMEZONE_KEY, timezone);
  } catch {
    /* private mode / quota - silently ignore, will just resend next session */
  }
}

export function subscriptionToPayload(subscription) {
  const json = subscription.toJSON();
  return {
    platform: 'WEB',
    token: json.endpoint,
    p256dh: json.keys && json.keys.p256dh,
    auth: json.keys && json.keys.auth,
  };
}

async function subscribeToPush(registration, publicKey) {
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
  }
  return api.registerPushSubscription(subscriptionToPayload(subscription));
}

/**
 * Orchestrates the whole web-push registration flow once per page session:
 * timezone sync (always, cheap and useful even without push), then - only
 * where supported and permitted - service worker registration + subscribe.
 * Safe to call from anywhere and repeatedly; all failures are swallowed so
 * the caller never needs a try/catch, and notifications.js keeps working
 * regardless of outcome.
 */
export async function ensurePushRegistration() {
  if (started) return;
  started = true;

  try {
    await syncTimezone();
  } catch {
    /* best-effort */
  }

  if (!isPushSupported()) return;

  try {
    // Check first so an unconfigured deployment (blank VAPID key, the task-009
    // default) never shows the user a permission popup that would accomplish
    // nothing - a denial here is sticky in the browser and would otherwise
    // block push permanently once VAPID keys are configured later.
    const { publicKey } = await api.getVapidPublicKey();
    if (!publicKey) return;
    const granted = await ensureNotificationPermission();
    if (!granted) return;
    const registration = await navigator.serviceWorker.register(SERVICE_WORKER_URL);
    await subscribeToPush(registration, publicKey);
  } catch {
    /* best-effort - the alarm poller fallback in notifications.js keeps working */
  }
}

/** Test-only: clears the once-per-session guard between test cases. */
export function __resetForTests() {
  started = false;
}
