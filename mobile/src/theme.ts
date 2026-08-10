import { useSyncExternalStore } from 'react';
import { Appearance } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Light/dark theme store, mirroring frontend/src/theme.js's semantics: an explicit user choice
 * persists (here: AsyncStorage instead of localStorage) and wins over the OS preference; with no
 * explicit choice, the OS preference is followed live. Unlike the web version there is no
 * `<html data-theme>` to flip a stylesheet on - every consumer calls `useTheme()` and builds its
 * `StyleSheet` from the returned `colors` object (see `useThemedStyles` below), so React re-renders
 * are what "switch" the theme.
 */

export type ThemeMode = 'light' | 'dark';

const lightColors = {
  background: '#FAF7FF',
  card: '#FFFFFF',
  border: '#E9D5FF',
  primary: '#9333EA',
  primaryDark: '#7E22CE',
  primaryLight: '#C084FC',
  text: '#2A1F3D',
  textMuted: '#6F5F93',
  error: '#DC2626',
  errorBg: '#FEE2E2',
  success: '#16A34A',
  successBg: '#DCFCE7',
  progressTrack: '#F3E8FF',
  overlay: 'rgba(42, 31, 61, 0.4)',
};

// Echoes frontend/src/theme.css's `:root[data-theme='dark']` tokens.
const darkColors: typeof lightColors = {
  background: '#151022',
  card: '#1F1837',
  border: '#352A59',
  primary: '#C084FC',
  primaryDark: '#A855F7',
  primaryLight: '#D8B4FE',
  text: '#ECE6F9',
  textMuted: '#A797CF',
  error: '#F472B6',
  errorBg: 'rgba(244, 114, 182, 0.14)',
  success: '#4ADE80',
  successBg: 'rgba(74, 222, 128, 0.14)',
  progressTrack: '#29204A',
  overlay: 'rgba(0, 0, 0, 0.5)',
};

export type ThemeColors = typeof lightColors;
const themes: Record<ThemeMode, ThemeColors> = { light: lightColors, dark: darkColors };

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 };
export const radii = { sm: 8, md: 12, lg: 16, pill: 999 };

const STORAGE_KEY = 'lilac-theme';
type Listener = () => void;
const listeners = new Set<Listener>();

let explicitMode: ThemeMode | null = null;
let systemMode: ThemeMode = Appearance.getColorScheme() === 'dark' ? 'dark' : 'light';
// False until the AsyncStorage read resolves, so callers (the root layout) can hold the splash
// screen up rather than flash system-then-stored theme on launch.
let hydrated = false;

function notify() {
  listeners.forEach((fn) => fn());
}

function subscribe(fn: Listener) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

AsyncStorage.getItem(STORAGE_KEY)
  .then((stored) => {
    if (stored === 'light' || stored === 'dark') explicitMode = stored;
  })
  .catch(() => {
    /* storage unavailable - fall back to system preference */
  })
  .finally(() => {
    hydrated = true;
    notify();
  });

Appearance.addChangeListener(({ colorScheme }) => {
  systemMode = colorScheme === 'dark' ? 'dark' : 'light';
  if (!explicitMode) notify();
});

export function getThemeMode(): ThemeMode {
  return explicitMode ?? systemMode;
}

export function isThemeHydrated(): boolean {
  return hydrated;
}

export async function setThemeMode(mode: ThemeMode | null) {
  explicitMode = mode;
  try {
    if (mode) await AsyncStorage.setItem(STORAGE_KEY, mode);
    else await AsyncStorage.removeItem(STORAGE_KEY);
  } catch {
    /* best effort */
  }
  notify();
}

export const toggleTheme = () => setThemeMode(getThemeMode() === 'dark' ? 'light' : 'dark');

export function useThemeMode(): ThemeMode {
  return useSyncExternalStore(subscribe, getThemeMode);
}

export function useThemeHydrated(): boolean {
  return useSyncExternalStore(subscribe, isThemeHydrated);
}

/** Whether the active theme follows the OS (no explicit user override yet). */
export function useIsSystemTheme(): boolean {
  return useSyncExternalStore(subscribe, () => explicitMode === null);
}

export function useTheme(): { mode: ThemeMode; colors: ThemeColors } {
  const mode = useThemeMode();
  return { mode, colors: themes[mode] };
}

/**
 * Builds a `StyleSheet` from the current theme's colors. `factory` is a pure function so its
 * result only needs recomputing when the theme mode actually changes - pass the same reference
 * across renders (define it at module scope, e.g. `const makeStyles = (colors) => StyleSheet.create({...})`).
 */
export function useThemedStyles<T>(factory: (colors: ThemeColors) => T): { colors: ThemeColors; styles: T } {
  const { colors } = useTheme();
  const styles = factory(colors);
  return { colors, styles };
}
