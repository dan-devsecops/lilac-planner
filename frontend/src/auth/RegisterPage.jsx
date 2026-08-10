import { useState } from 'react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import * as nativeAuth from './nativeAuth.js';

export default function RegisterPage() {
  const [form, setForm] = useState({ username: '', email: '', displayName: '', password: '', confirm: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  async function onSubmit(e) {
    e.preventDefault();
    setError(null);
    if (form.password !== form.confirm) {
      setError('Passwords do not match');
      return;
    }
    setBusy(true);
    try {
      await nativeAuth.register({
        username: form.username,
        email: form.email,
        displayName: form.displayName,
        password: form.password,
      });
      // Auto sign-in after registering, then enter the app.
      await nativeAuth.login(form.username, form.password);
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
        <h2>Create your account</h2>
        {error && <div className="auth-error">{error}</div>}
        <label className="auth-field">
          <span>Username</span>
          <input value={form.username} onChange={set('username')} autoFocus required />
        </label>
        <label className="auth-field">
          <span>Email</span>
          <input type="email" value={form.email} onChange={set('email')} required />
        </label>
        <label className="auth-field">
          <span>Display name (optional)</span>
          <input value={form.displayName} onChange={set('displayName')} />
        </label>
        <label className="auth-field">
          <span>Password</span>
          <input type="password" value={form.password} onChange={set('password')} minLength={8} required />
        </label>
        <label className="auth-field">
          <span>Confirm password</span>
          <input type="password" value={form.confirm} onChange={set('confirm')} minLength={8} required />
        </label>
        <button type="submit" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <div className="auth-links">
          <Link to="/login">Already have an account? Sign in</Link>
        </div>
      </form>
    </div>
  );
}
