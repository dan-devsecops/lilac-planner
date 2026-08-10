import { useState } from 'react';
import { Link } from 'react-router-dom';
import * as nativeAuth from './nativeAuth.js';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await nativeAuth.forgotPassword(email);
    } catch {
      /* never reveal whether the email exists */
    }
    setSent(true);
    setBusy(false);
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>🌸 Lilac Planner</h1>
        <h2>Reset your password</h2>
        {sent ? (
          <div className="auth-success">
            If an account exists for that email, a reset link is on its way.
          </div>
        ) : (
          <>
            <label className="auth-field">
              <span>Email</span>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus required />
            </label>
            <button type="submit" disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
          </>
        )}
        <div className="auth-links">
          <Link to="/login">Back to sign in</Link>
        </div>
      </form>
    </div>
  );
}
