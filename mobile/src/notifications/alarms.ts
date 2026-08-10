import { Platform } from 'react-native';
import Constants, { AppOwnership } from 'expo-constants';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { TaskDto } from '../api/types';

/**
 * Local scheduled-notification alarms - the mobile analogue of frontend/src/notifications.js.
 * The web app polls every 15s while the tab is open; a backgrounded mobile app can't do that, so
 * each eligible task instead gets a genuine OS-scheduled one-shot notification.
 * `reconcileDayNotifications` is called whenever a day's tasks change and does the
 * schedule/cancel bookkeeping so callers never have to think about individual notification ids.
 *
 * `expo-notifications` itself throws at import time on Android inside Expo Go - remote push was
 * dropped from Expo Go in SDK 53, and merely importing the module (not just registering for a
 * push token) trips that check, which would otherwise crash the whole app in the sandbox everyone
 * currently uses to run this project (no dev-client build exists yet). Local scheduled
 * notifications work fine in a real dev-client/standalone build, so the module is `require`d
 * lazily and only outside that one environment. `Constants.appOwnership` (not the recommended-but-
 * coarser `executionEnvironment`) is used deliberately: it's the only API that distinguishes
 * literal Expo Go from a dev-client build, and dev-client fully supports this on Android.
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

const MAP_KEY = 'lilac-notif-map-v1';

interface ScheduleEntry {
  notificationId: string;
  signature: string;
}

type ScheduleMap = Record<string, ScheduleEntry>; // key: `${date}:${taskId}`

export function configureNotifications() {
  const Notifications = getNotifications();
  if (!Notifications) return;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowAlert: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
      shouldShowBanner: true,
      shouldShowList: true,
    }),
  });
}

/** Call from a user gesture (e.g. confirming a reminder time) - OS permission prompts are ignored otherwise. */
export async function ensureNotificationPermission(): Promise<boolean> {
  const Notifications = getNotifications();
  if (!Notifications) return false;
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;
  if (!current.canAskAgain) return false;
  const requested = await Notifications.requestPermissionsAsync();
  return requested.granted;
}

function key(date: string, taskId: string): string {
  return `${date}:${taskId}`;
}

function signature(task: TaskDto): string {
  return `${task.title}|${task.points}|${task.scheduledTime}`;
}

function targetDate(date: string, scheduledTime: string): Date | null {
  const [hh, mm] = scheduledTime.split(':').map(Number);
  if (Number.isNaN(hh) || Number.isNaN(mm)) return null;
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d, hh, mm, 0, 0);
}

async function loadMap(): Promise<ScheduleMap> {
  try {
    const raw = await AsyncStorage.getItem(MAP_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

async function saveMap(map: ScheduleMap) {
  try {
    await AsyncStorage.setItem(MAP_KEY, JSON.stringify(map));
  } catch {
    /* best effort */
  }
}

async function cancelSafe(notifications: typeof import('expo-notifications'), notificationId: string) {
  try {
    await notifications.cancelScheduledNotificationAsync(notificationId);
  } catch {
    /* already fired or cancelled */
  }
}

/**
 * Reconciles one day's scheduled reminders against its current task list: cancels notifications
 * for tasks that were deleted, completed, lost their scheduledTime, or whose time already passed;
 * (re)schedules the rest when new or changed (title/points/time). Never schedules into the past -
 * an OS trigger can't fire retroactively, unlike the web poller's forgiving catch-up window.
 */
export async function reconcileDayNotifications(date: string, tasks: TaskDto[]) {
  const Notifications = getNotifications();
  if (!Notifications) return;

  const map = await loadMap();
  const now = new Date();
  const presentKeys = new Set(tasks.map((t) => key(date, t.id)));

  for (const k of Object.keys(map)) {
    if (k.startsWith(`${date}:`) && !presentKeys.has(k)) {
      await cancelSafe(Notifications, map[k].notificationId);
      delete map[k];
    }
  }

  for (const task of tasks) {
    const k = key(date, task.id);
    const trigger = !task.completed && task.scheduledTime ? targetDate(date, task.scheduledTime) : null;
    const eligible = !!trigger && trigger.getTime() > now.getTime();

    if (!eligible) {
      if (map[k]) {
        await cancelSafe(Notifications, map[k].notificationId);
        delete map[k];
      }
      continue;
    }

    const sig = signature(task);
    if (map[k]?.signature === sig) continue;
    if (map[k]) await cancelSafe(Notifications, map[k].notificationId);

    const notificationId = await Notifications.scheduleNotificationAsync({
      content: {
        title: '🌸 Lilac Planner reminder',
        body: `${task.title} (${task.points} pts)`,
      },
      trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: trigger! },
    });
    map[k] = { notificationId, signature: sig };
  }

  await saveMap(map);
}
