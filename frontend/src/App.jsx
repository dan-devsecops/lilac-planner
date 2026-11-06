import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { format } from 'date-fns';
import DayView from './components/DayView.jsx';
import Statistics from './components/Statistics.jsx';
import UserInfo from './components/UserInfo.jsx';
import ThemeToggle from './components/ThemeToggle.jsx';
import { isNative } from './auth/config.js';
import * as nativeAuth from './auth/nativeAuth.js';
import LoginPage from './auth/LoginPage.jsx';
import RegisterPage from './auth/RegisterPage.jsx';
import ForgotPasswordPage from './auth/ForgotPasswordPage.jsx';
import ResetPasswordPage from './auth/ResetPasswordPage.jsx';
import AccountPage from './auth/AccountPage.jsx';
import { ensurePushRegistration } from './push.js';

export default function App() {
  const [today, setToday] = useState(() => format(new Date(), 'yyyy-MM-dd'));

  // Re-sync at midnight so the "Today" nav link never goes stale.
  useEffect(() => {
    const now = new Date();
    const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    const id = setTimeout(() => setToday(format(new Date(), 'yyyy-MM-dd')), midnight - now);
    return () => clearTimeout(id);
  }, [today]);

  // Signed in (or auth is off): register for web push + sync timezone once per session.
  // notifications.js's in-page poller keeps working regardless of the outcome.
  useEffect(() => {
    if (isNative && !nativeAuth.isAuthenticated()) return;
    ensurePushRegistration();
  }, []);

  // Native auth, not signed in → only the unauthenticated auth screens are reachable.
  if (isNative && !nativeAuth.isAuthenticated()) {
    return (
      <div className="app">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>🌸 Lilac Planner</h1>
        <div className="app-header-right">
          <nav className="nav">
            <NavLink to={`/day/${today}`} className={({ isActive }) => isActive ? 'active' : ''}>
              Today
            </NavLink>
            <NavLink to="/statistics" className={({ isActive }) => isActive ? 'active' : ''}>
              Statistics
            </NavLink>
          </nav>
          <UserInfo />
          <ThemeToggle />
        </div>
      </header>
      <Routes>
        <Route path="/" element={<Navigate to={`/day/${today}`} replace />} />
        <Route path="/day/:date" element={<DayView />} />
        <Route path="/statistics" element={<Statistics />} />
        <Route path="/account" element={<AccountPage />} />
        {/* Signed in: auth screens just bounce back into the app. */}
        <Route path="/login" element={<Navigate to={`/day/${today}`} replace />} />
        <Route path="/register" element={<Navigate to={`/day/${today}`} replace />} />
        <Route path="*" element={<Navigate to={`/day/${today}`} replace />} />
      </Routes>
    </div>
  );
}
