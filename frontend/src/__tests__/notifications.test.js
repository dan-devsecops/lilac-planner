import { describe, expect, it } from 'vitest';
import { ackKey, shouldFireNow } from '../notifications.js';

const at = (iso) => new Date(iso);

describe('shouldFireNow', () => {
  it('returns false for falsy / missing tasks', () => {
    expect(shouldFireNow(null)).toBe(false);
    expect(shouldFireNow(undefined)).toBe(false);
    expect(shouldFireNow({})).toBe(false);
  });

  it('returns false when the task has no scheduledTime', () => {
    expect(shouldFireNow({ id: 1, title: 't', points: 1, completed: false })).toBe(false);
  });

  it('returns false when the task is already completed', () => {
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '12:00:00', completed: true },
      at('2026-05-13T12:00:00'),
    )).toBe(false);
  });

  it('fires exactly at the scheduled minute', () => {
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '18:54:00', completed: false },
      at('2026-05-13T18:54:00'),
    )).toBe(true);
  });

  it('fires up to 30 seconds before the scheduled minute', () => {
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '18:54:00', completed: false },
      at('2026-05-13T18:53:31'),
    )).toBe(true);
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '18:54:00', completed: false },
      at('2026-05-13T18:53:29'),
    )).toBe(false);
  });

  it('forgives up to 5 minutes of lateness (page just opened, alarm overdue)', () => {
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '18:54:00', completed: false },
      at('2026-05-13T18:58:59'),
    )).toBe(true);
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '18:54:00', completed: false },
      at('2026-05-13T19:00:00'),
    )).toBe(false);
  });

  it('does not fire when far away in either direction', () => {
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '08:00:00', completed: false },
      at('2026-05-13T18:54:00'),
    )).toBe(false);
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: '23:00:00', completed: false },
      at('2026-05-13T18:54:00'),
    )).toBe(false);
  });

  it('handles HH:MM and HH:MM:SS formats interchangeably', () => {
    const exact = at('2026-05-13T14:30:00');
    expect(shouldFireNow({ id: 1, title: 't', points: 1, scheduledTime: '14:30', completed: false }, exact)).toBe(true);
    expect(shouldFireNow({ id: 1, title: 't', points: 1, scheduledTime: '14:30:00', completed: false }, exact)).toBe(true);
  });

  it('returns false for malformed time strings', () => {
    expect(shouldFireNow(
      { id: 1, title: 't', points: 1, scheduledTime: 'nope', completed: false },
      at('2026-05-13T12:00:00'),
    )).toBe(false);
  });
});

describe('ackKey', () => {
  it('uniquely identifies an alarm by (date, taskId)', () => {
    expect(ackKey('2026-05-13', 1)).toBe('2026-05-13:1');
    expect(ackKey('2026-05-13', 2)).not.toBe(ackKey('2026-05-13', 1));
    expect(ackKey('2026-05-14', 1)).not.toBe(ackKey('2026-05-13', 1));
  });
});
