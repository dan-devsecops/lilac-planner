const FIRE_WINDOW_BEFORE_MS = 30_000;     // fire up to 30s before the scheduled minute
const FIRE_WINDOW_AFTER_MS  = 5 * 60_000; // ...and forgive being up to 5 min late
const ACK_KEY = 'lilac-planner-acks-v1';

/**
 * Pure predicate: should the alarm fire for this task right now?
 * Tasks fire once per (date, taskId) - the caller is expected to keep
 * acked-keys in localStorage so reloads don't double-fire.
 */
export function shouldFireNow(task, now = new Date()) {
  if (!task || !task.scheduledTime || task.completed) return false;
  const [hh, mm] = String(task.scheduledTime).split(':').map(Number);
  if (Number.isNaN(hh) || Number.isNaN(mm)) return false;
  const target = new Date(now);
  target.setHours(hh, mm, 0, 0);
  const delta = now.getTime() - target.getTime();
  return delta >= -FIRE_WINDOW_BEFORE_MS && delta <= FIRE_WINDOW_AFTER_MS;
}

export function ackKey(date, taskId) {
  return `${date}:${taskId}`;
}

export function loadAcks() {
  try {
    return new Set(JSON.parse(localStorage.getItem(ACK_KEY) || '[]'));
  } catch {
    return new Set();
  }
}

export function saveAcks(set) {
  try {
    localStorage.setItem(ACK_KEY, JSON.stringify([...set]));
  } catch {
    /* private mode / quota / disabled - silently ignore */
  }
}

/**
 * Ask the browser for Notification permission. Call this inside a user
 * gesture (form submit, button click) - browsers ignore requests made on
 * page load.
 */
export async function ensureNotificationPermission() {
  if (typeof window === 'undefined' || !('Notification' in window)) return false;
  if (Notification.permission === 'granted') return true;
  if (Notification.permission === 'denied') return false;
  try {
    const res = await Notification.requestPermission();
    return res === 'granted';
  } catch {
    return false;
  }
}

/**
 * Start polling for due alarms. Returns a stop() function.
 * - getTasks/getDate are read fresh every tick, so the caller can swap
 *   them with refs without restarting the poller.
 * - onFire(task) is called at most once per (date, taskId) - backed by
 *   localStorage, so reloading the page still respects acks.
 */
export function startAlarmPoller({ getTasks, getDate, onFire, intervalMs = 15_000 }) {
  if (typeof window === 'undefined') return () => {};
  const acks = loadAcks();

  const tick = () => {
    const tasks = getTasks() || [];
    const date = getDate();
    const now = new Date();
    for (const task of tasks) {
      const key = ackKey(date, task.id);
      if (acks.has(key)) continue;
      if (shouldFireNow(task, now)) {
        acks.add(key);
        saveAcks(acks);
        try { onFire(task); } catch { /* listener swallowed */ }
      }
    }
  };

  tick(); // immediate so a freshly-loaded page surfaces overdue alarms
  const id = window.setInterval(tick, intervalMs);
  return () => window.clearInterval(id);
}

/**
 * Drive all the side effects of an alarm: OS notification, audible chime,
 * and a flashing tab title. Caller is responsible for any in-page UI.
 */
export function fireAlarm(task) {
  const title = '🌸 Lilac Planner reminder';
  const body = `${task.title} (${task.points} pts)`;
  if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
    try {
      new Notification(title, {
        body,
        icon: '/favicon.svg',
        tag: `task-${task.id}`,
        requireInteraction: true,
      });
    } catch {
      /* fall through to audible + tab flash */
    }
  }
  chime();
  flashTab(body);
}

function chime() {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return;
    const ctx = new Ctx();
    const play = (freq, startAt, duration = 0.35) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.type = 'sine';
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, ctx.currentTime + startAt);
      gain.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + startAt + 0.04);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + startAt + duration);
      osc.start(ctx.currentTime + startAt);
      osc.stop(ctx.currentTime + startAt + duration + 0.05);
    };
    // a soft two-note chime
    play(880, 0);
    play(1175, 0.25);
  } catch {
    /* audio not available in this environment */
  }
}

function flashTab(text) {
  if (typeof document === 'undefined') return;
  const original = document.title;
  let toggled = false;
  const id = window.setInterval(() => {
    document.title = toggled ? original : `⏰ ${text}`;
    toggled = !toggled;
  }, 1000);
  const restore = () => {
    window.clearInterval(id);
    document.title = original;
    window.removeEventListener('focus', restore);
  };
  window.addEventListener('focus', restore);
  window.setTimeout(restore, 30_000);
}
