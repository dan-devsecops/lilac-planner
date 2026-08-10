import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { addDays, format, parseISO } from 'date-fns';
import { api } from '../api.js';
import { nextThreshold } from '../stickers.js';
import {
  ensureNotificationPermission,
  fireAlarm,
  startAlarmPoller,
} from '../notifications.js';
import DateNav from './DateNav.jsx';
import TaskItem from './TaskItem.jsx';
import StickerShelf from './StickerShelf.jsx';

function movedTitle(title) {
  const m = title.match(/^(.*) \(moved(?: x(\d+))?\)$/);
  if (m) {
    const count = m[2] ? parseInt(m[2], 10) : 1;
    return `${m[1]} (moved x${count + 1})`;
  }
  return `${title} (moved)`;
}

const RECURRENCE_OPTIONS = [
  { value: 'NONE',    label: 'Does not repeat' },
  { value: 'DAILY',   label: 'Daily' },
  { value: 'WEEKLY',  label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'YEARLY',  label: 'Yearly' },
];

export default function DayView() {
  const { date } = useParams();
  const [day, setDay] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [title, setTitle] = useState('');
  const [points, setPoints] = useState(1);
  const [scheduledTime, setScheduledTime] = useState('');
  const [recurrence, setRecurrence] = useState('NONE');
  const [submitting, setSubmitting] = useState(false);

  // Drag-and-drop transient state
  const [draggingId, setDraggingId] = useState(null);
  const [dropTarget, setDropTarget] = useState(null); // holds the target task id and whether the drop lands before it

  // Alarm toast queue
  const [alarms, setAlarms] = useState([]);

  // Transient one-shot toast (e.g. "Copied to 2026-05-16 ✓")
  const [flash, setFlash] = useState(null);

  // Refs the alarm poller reads on every tick (so it doesn't restart on every state change)
  const tasksRef = useRef([]);
  const dateRef = useRef(date);

  // Fetch day data whenever the date changes. An AbortController is created on
  // each run so that if the user navigates away before the response arrives, the
  // in-flight request is cancelled and its (now stale) result is discarded.
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    api.getDay(date, controller.signal)
      .then((d) => { setDay(d); setError(null); })
      .catch((e) => { if (e.name !== 'AbortError') setError(e.message); })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [date]);

  // refresh() is only called from mutation handlers when a 404 indicates
  // out-of-sync state. It does not carry a signal - the user is still on the
  // same page and we always want the result.
  const refresh = useCallback(() => {
    setLoading(true);
    api.getDay(date)
      .then((d) => { setDay(d); setError(null); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [date]);

  useEffect(() => { tasksRef.current = day?.tasks || []; }, [day]);
  useEffect(() => { dateRef.current = date; }, [date]);

  // isToday must stay accurate after midnight without a page reload. We keep a
  // "clock tick" counter that is incremented exactly at midnight each day so
  // that the derived value re-evaluates even when the date route param hasn't
  // changed.
  const [midnightTick, setMidnightTick] = useState(0);
  useEffect(() => {
    function scheduleNextMidnight() {
      const now = new Date();
      const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
      const msUntilMidnight = tomorrow - now;
      return window.setTimeout(() => {
        setMidnightTick((t) => t + 1);
        scheduleNextMidnight();
      }, msUntilMidnight);
    }
    const id = scheduleNextMidnight();
    return () => window.clearTimeout(id);
  }, []);

  const isToday = useMemo(() => date === format(new Date(), 'yyyy-MM-dd'), [date, midnightTick]);

  // Single long-lived poller - only spins up on today's view.
  useEffect(() => {
    if (!isToday) return undefined;
    return startAlarmPoller({
      getTasks: () => tasksRef.current,
      getDate: () => dateRef.current,
      onFire: (task) => {
        fireAlarm(task);
        setAlarms((current) => [...current, { ...task, _key: `${task.id}-${Date.now()}` }]);
      },
    });
  }, [isToday]);

  const dismissAlarm = (key) =>
    setAlarms((current) => current.filter((a) => a._key !== key));

  // Auto-dismiss the flash toast after a couple of seconds.
  useEffect(() => {
    if (!flash) return undefined;
    const id = window.setTimeout(() => setFlash(null), 2500);
    return () => window.clearTimeout(id);
  }, [flash]);

  const handleAdd = async (e) => {
    e.preventDefault();
    const trimmed = title.trim();
    if (!trimmed || submitting) return;
    setSubmitting(true);
    try {
      if (scheduledTime) {
        // User gesture - best time to ask the browser for permission.
        await ensureNotificationPermission();
      }
      const payload = {
        title: trimmed,
        points: Number(points) || 1,
        scheduledTime: scheduledTime ? `${scheduledTime}:00` : null,
        recurrence,
      };
      const updated = await api.addTask(date, payload);
      setDay(updated);
      setTitle('');
      setPoints(1);
      setScheduledTime('');
      setRecurrence('NONE');
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggle = async (taskId, completed) => {
    try {
      const updatedDay = await api.updateTask(date, taskId, { completed });
      const sorted = [
        ...updatedDay.tasks.filter((t) => t.completed),
        ...updatedDay.tasks.filter((t) => !t.completed),
      ];
      setDay({ ...updatedDay, tasks: sorted });
      setDay(await api.reorderTasks(date, sorted.map((t) => t.id)));
    } catch (err) {
      if (err.message.startsWith('404')) refresh();
      else { setError(err.message); refresh(); }
    }
  };

  const handleClearAlarm = async (taskId) => {
    try { setDay(await api.updateTask(date, taskId, { clearScheduledTime: true })); }
    catch (err) {
      if (err.message.startsWith('404')) refresh();
      else setError(err.message);
    }
  };

  const handlePoints = async (taskId, value) => {
    try { setDay(await api.updateTask(date, taskId, { points: value })); }
    catch (err) {
      if (err.message.startsWith('404')) refresh();
      else setError(err.message);
    }
  };

  const handleTitle = async (taskId, newTitle) => {
    try { setDay(await api.updateTask(date, taskId, { title: newTitle })); }
    catch (err) {
      if (err.message.startsWith('404')) refresh();
      else setError(err.message);
    }
  };

  const handleDelete = async (taskId) => {
    try { setDay(await api.deleteTask(date, taskId)); }
    catch (err) {
      if (err.message.startsWith('404')) refresh(); // already gone in another tab - reload silently
      else setError(err.message);
    }
  };

  /**
   * Copy a task forward by one day. Reuses POST /api/v1/days/{date}/tasks -
   * no backend change needed. The copy starts uncompleted and as a one-off
   * (we don't propagate recurrence; that would surprise the user).
   */
  const handleCopyToNextDay = async (task) => {
    const nextDate = format(addDays(parseISO(date), 1), 'yyyy-MM-dd');
    try {
      await api.addTask(nextDate, {
        title: movedTitle(task.title),
        points: task.points,
        scheduledTime: task.scheduledTime,
        recurrence: 'NONE',
      });
      setFlash({ key: `copy-${task.id}-${Date.now()}`, message: `Copied to ${nextDate} ✓` });
    } catch (err) {
      setError(err.message);
    }
  };

  // --- Drag and drop ---

  const handleDragStart = (id) => setDraggingId(id);
  const handleDragOver = (id, before) => {
    if (draggingId == null || draggingId === id) return;
    setDropTarget((curr) => (curr && curr.id === id && curr.before === before ? curr : { id, before }));
  };
  const handleDragLeave = (id) => {
    setDropTarget((curr) => (curr && curr.id === id ? null : curr));
  };
  const handleDragEnd = () => {
    setDraggingId(null);
    setDropTarget(null);
  };
  const dropEdgeFor = (t) => {
    if (!dropTarget || dropTarget.id !== t.id) return null;
    return dropTarget.before ? 'before' : 'after';
  };
  const handleKeyboardMove = useCallback((taskId, direction) => {
    if (!day) return;
    const ids = day.tasks.map((t) => t.id);
    const idx = ids.indexOf(taskId);
    if (direction === 'up' && idx <= 0) return;
    if (direction === 'down' && idx >= ids.length - 1) return;
    const newIds = [...ids];
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    [newIds[idx], newIds[swapIdx]] = [newIds[swapIdx], newIds[idx]];
    const optimistic = { ...day, tasks: newIds.map((id) => day.tasks.find((t) => t.id === id)) };
    setDay(optimistic);
    api.reorderTasks(date, newIds).then(setDay).catch((err) => { setError(err.message); refresh(); });
  }, [day, date, refresh]);

  const handleDrop = async (targetId) => {
    const drag = draggingId;
    const target = dropTarget;
    setDraggingId(null);
    setDropTarget(null);
    if (!day || drag == null || drag === targetId) return;

    const ids = day.tasks.map((t) => t.id);
    const filtered = ids.filter((id) => id !== drag);
    let targetIdx = filtered.indexOf(targetId);
    if (targetIdx < 0) return;
    if (target && target.id === targetId && !target.before) targetIdx += 1;
    filtered.splice(targetIdx, 0, drag);

    const optimistic = {
      ...day,
      tasks: filtered.map((id) => day.tasks.find((t) => t.id === id)),
    };
    setDay(optimistic);
    try { setDay(await api.reorderTasks(date, filtered)); }
    catch (err) { setError(err.message); refresh(); }
  };

  if (loading && !day) return <div className="card empty">Loading…</div>;
  if (error && !day) return <div className="card empty" style={{ color: 'var(--color-error)' }}>Error: {error}</div>;
  if (!day) return null;

  const total = day.totalPoints;
  const totalAvailable = day.tasks.reduce((s, t) => s + (t.points || 0), 0);
  const next = nextThreshold(total);
  const pct = totalAvailable === 0 ? 0 : Math.min(100, Math.max(0, (total / totalAvailable) * 100));

  return (
    <>
      <DateNav date={date} />
      <div className="card">
        {error && (
          <div className="error-banner">
            ⚠ {error} <button className="ghost" onClick={() => setError(null)}>dismiss</button>
          </div>
        )}
        {day.tasks.length === 0 ? (
          <div className="empty">
            <div className="empty-emoji">🪻</div>
            No tasks yet - add your first one below 💜
          </div>
        ) : (
          <ul className="task-list">
            {day.tasks.map((t) => (
              <TaskItem
                key={t.id}
                task={t}
                onToggle={handleToggle}
                onPoints={handlePoints}
                onTitle={handleTitle}
                onClearAlarm={handleClearAlarm}
                onDelete={handleDelete}
                onCopyToNextDay={handleCopyToNextDay}
                onKeyboardMove={(direction) => handleKeyboardMove(t.id, direction)}
                isDragging={draggingId === t.id}
                dropEdge={dropEdgeFor(t)}
                onDragStart={handleDragStart}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onDragEnd={handleDragEnd}
              />
            ))}
          </ul>
        )}

        <form className="add-task-form" onSubmit={handleAdd}>
          <input
            type="text"
            placeholder="Add a new task…"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
            required
          />
          <input
            type="number"
            min="0"
            step="1"
            value={points}
            onChange={(e) => setPoints(e.target.value)}
            title="Points"
          />
          <input
            type="time"
            value={scheduledTime}
            onChange={(e) => setScheduledTime(e.target.value)}
            title="Optional reminder time"
          />
          <select
            value={recurrence}
            onChange={(e) => setRecurrence(e.target.value)}
            title="Recurrence"
          >
            {RECURRENCE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <button type="submit" disabled={submitting || !title.trim()}>Add</button>
        </form>

        <div className="totals-bar">
          <span>
            Earned <b>{total}</b> / <b>{totalAvailable}</b> pts
          </span>
          <div
            className="progress-wrap"
            title={`${total} / ${totalAvailable} pts (${Math.round(pct)}%)`}
          >
            <div className="progress-fill" style={{ width: `${pct}%` }} />
          </div>
          <span>Next sticker: {next} pts</span>
        </div>

        <StickerShelf earned={day.earnedStickers} totalPoints={total} />
      </div>

      {alarms.length > 0 && (
        <div className="alarm-toast-stack" role="alert" aria-live="assertive">
          {alarms.map((a) => (
            <div className="alarm-toast" key={a._key}>
              <div className="alarm-toast-icon">⏰</div>
              <div className="alarm-toast-body">
                <strong>{a.title}</strong>
                <small>{a.scheduledTime?.slice(0, 5)} · {a.points} pts</small>
              </div>
              <button className="ghost" onClick={() => dismissAlarm(a._key)}>Got it</button>
            </div>
          ))}
        </div>
      )}

      {flash && (
        <div className="flash-toast" role="status" aria-live="polite" key={flash.key}>
          {flash.message}
        </div>
      )}
    </>
  );
}
