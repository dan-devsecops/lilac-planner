import { toggleTheme, useTheme } from '../theme.js';

export default function ThemeToggle() {
  const dark = useTheme() === 'dark';
  const label = dark ? 'Switch to light mode' : 'Switch to dark mode';
  return (
    <button
      type="button"
      className="ghost theme-toggle"
      onClick={toggleTheme}
      title={label}
      aria-label={label}
    >
      {dark ? '☀️' : '🌙'}
    </button>
  );
}
