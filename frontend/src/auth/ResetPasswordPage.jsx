import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import * as nativeAuth from './nativeAuth.js';

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setBusy(true);
    try {
      await nativeAuth.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(err.message);
    }
    setBusy(false);
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>🌸 Lilac Planner</h1>
        <h2>Choose a new password</h2>
        {!token && <div className="auth-error">Missing or invalid reset link.</div>}
        {error && <div className="auth-error">{error}</div>}
        {done ? (
          <div className="auth-success">Your password has been reset. You can sign in now.</div>
        ) : (
          <>
            <label className="auth-field">
              <span>New password</span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                     minLength={8} required disabled={!token} autoFocus />
            </label>
            <label className="auth-field">
              <span>Confirm new password</span>
              <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
                     minLength={8} required disabled={!token} />
            </label>
            <button type="submit" disabled={busy || !token}>{busy ? 'Resetting…' : 'Reset password'}</button>
          </>
        )}
        <div className="auth-links">
          <Link to="/login">Back to sign in</Link>
        </div>
      </form>
    </div>
  );
}
