import { useEffect, useState } from 'react';
import * as nativeAuth from './nativeAuth.js';
import { isNative } from './config.js';
import { api } from '../api.js';
import { detectTimezone } from '../push.js';

function ChangePassword() {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  async function onSubmit(e) {
    e.preventDefault();
    setMsg(null); setError(null);
    if (next !== confirm) { setError('Passwords do not match'); return; }
    try {
      await nativeAuth.changePassword(current, next);
      setMsg('Password changed.');
      setCurrent(''); setNext(''); setConfirm('');
    } catch (err) { setError(err.message); }
  }

  return (
    <form className="auth-card" onSubmit={onSubmit}>
      <h2>Change password</h2>
      {msg && <div className="auth-success">{msg}</div>}
      {error && <div className="auth-error">{error}</div>}
      <label className="auth-field">
        <span>Current password</span>
        <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
      </label>
      <label className="auth-field">
        <span>New password</span>
        <input type="password" value={next} onChange={(e) => setNext(e.target.value)} minLength={8} required />
      </label>
      <label className="auth-field">
        <span>Confirm new password</span>
        <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} minLength={8} required />
      </label>
      <button type="submit">Change password</button>
    </form>
  );
}

function AdminReset() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  async function onSubmit(e) {
    e.preventDefault();
    setMsg(null); setError(null);
    try {
      await nativeAuth.adminResetPassword(username, password);
      setMsg(`Password reset for ${username}.`);
      setUsername(''); setPassword('');
    } catch (err) { setError(err.message); }
  }

  return (
    <form className="auth-card" onSubmit={onSubmit}>
      <h2>Admin: reset a user's password</h2>
      {msg && <div className="auth-success">{msg}</div>}
      {error && <div className="auth-error">{error}</div>}
      <label className="auth-field">
        <span>Username</span>
        <input value={username} onChange={(e) => setUsername(e.target.value)} required />
      </label>
      <label className="auth-field">
        <span>New password</span>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required />
      </label>
      <button type="submit">Reset password</button>
    </form>
  );
}

function TimezoneSettings() {
  const [timezone, setTimezone] = useState('');
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api.me()
      .then((user) => {
        if (cancelled) return;
        setCurrent(user.timezone || null);
        setTimezone(user.timezone || '');
      })
      .catch((err) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  async function onSubmit(e) {
    e.preventDefault();
    setMsg(null); setError(null);
    const value = timezone.trim();
    setSaving(true);
    try {
      await api.updateTimezone(value);
      setCurrent(value);
      setMsg('Timezone updated.');
    } catch (err) {
      setError(err.message);
    }
    setSaving(false);
  }

  function useBrowserTimezone() {
    const detected = detectTimezone();
    if (detected) setTimezone(detected);
  }

  return (
    <div className="auth-card">
      <h2>Timezone</h2>
      <p className="auth-hint">
        Used to work out "today" for task alarm push notifications.
        {!loading && ` Currently: ${current || 'not set (using server default)'}.`}
      </p>
      {msg && <div className="auth-success">{msg}</div>}
      {error && <div className="auth-error">{error}</div>}
      {!loading && (
        <form onSubmit={onSubmit}>
          <label className="auth-field">
            <span>IANA timezone (e.g. Europe/Prague)</span>
            <input value={timezone} onChange={(e) => setTimezone(e.target.value)} required />
          </label>
          <div className="auth-actions">
            <button type="button" className="ghost" onClick={useBrowserTimezone}>Use browser timezone</button>
            <button type="submit" disabled={saving || !timezone.trim()}>Save</button>
          </div>
        </form>
      )}
    </div>
  );
}

function DeviceList() {
  const [devices, setDevices] = useState(null);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api.listPushSubscriptions()
      .then((list) => { if (!cancelled) setDevices(list); })
      .catch((err) => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, []);

  async function onDelete(id) {
    setBusyId(id);
    setError(null);
    try {
      await api.deletePushSubscription(id);
      setDevices((prev) => prev.filter((d) => d.id !== id));
    } catch (err) {
      setError(err.message);
    }
    setBusyId(null);
  }

  return (
    <div className="auth-card">
      <h2>Registered devices</h2>
      {error && <div className="auth-error">{error}</div>}
      {devices === null && !error && <p className="auth-hint">Loading…</p>}
      {devices !== null && devices.length === 0 && (
        <p className="auth-hint">No devices registered for push notifications yet.</p>
      )}
      {devices !== null && devices.length > 0 && (
        <ul className="device-list">
          {devices.map((d) => (
            <li key={d.id} className="device-list-item">
              <span className="device-list-platform">
                {d.platform === 'WEB' ? '🌐 Browser' : '📱 Mobile'}
              </span>
              <span className="device-list-meta">
                registered {new Date(d.createdAt).toLocaleDateString()}
              </span>
              <button
                type="button"
                className="ghost"
                disabled={busyId === d.id}
                onClick={() => onDelete(d.id)}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function AccountPage() {
  return (
    <div className="account-page">
      <TimezoneSettings />
      <DeviceList />
      {isNative && <ChangePassword />}
      {isNative && nativeAuth.isAdmin() && <AdminReset />}
    </div>
  );
}
