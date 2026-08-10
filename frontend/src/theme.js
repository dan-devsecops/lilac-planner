import { useSyncExternalStore } from 'react';

/**
 * Tiny light/dark theme store. The active theme lives on
 * <html data-theme="..."> so theme.css tokens switch instantly;
 * the explicit user choice persists in localStorage and falls back
 * to the OS preference when unset.
 */

const STORAGE_KEY = 'lilac-theme';
const listeners = new Set();

export function getTheme() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') return stored;
  } catch {
    // storage unavailable (private mode) - fall through to OS preference
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ? 'dark' : 'light';
}

export function setTheme(theme) {
  try { localStorage.setItem(STORAGE_KEY, theme); } catch { /* best effort */ }
  apply(theme);
  listeners.forEach((fn) => fn());
}

export const toggleTheme = () => setTheme(getTheme() === 'dark' ? 'light' : 'dark');

/** Call once before the first render so the app never flashes the wrong theme. */
export const applyStoredTheme = () => apply(getTheme());

function apply(theme) {
  document.documentElement.dataset.theme = theme;
}

function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export const useTheme = () => useSyncExternalStore(subscribe, getTheme);
