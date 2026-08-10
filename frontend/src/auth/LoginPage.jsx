import { useState } from 'react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import * as nativeAuth from './nativeAuth.js';

export default function LoginPage() {
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await nativeAuth.login(login, password);
      // Full reload so App re-evaluates auth state from storage.
      window.location.assign(`/day/${format(new Date(), 'yyyy-MM-dd')}`);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>🌸 Lilac Planner</h1>
        <h2>Sign in</h2>
        {error && <div className="auth-error">{error}</div>}
        <label className="auth-field">
          <span>Username or email</span>
          <input value={login} onChange={(e) => setLogin(e.target.value)} autoFocus required />
        </label>
        <label className="auth-field">
          <span>Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <div className="auth-links">
          <Link to="/forgot-password">Forgot password?</Link>
          <Link to="/register">Create an account</Link>
        </div>
      </form>
    </div>
  );
}
