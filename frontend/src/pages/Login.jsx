import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api.js';
import { homeFor, useAuth } from '../auth.jsx';
import { useI18n } from '../i18n.jsx';
import { ErrorBox } from '../components/ui.jsx';

const DEMO = [
  ['Citizen', 'citizen1@jansunwai.demo'], ['Roads officer', 'roads.officer@jansunwai.demo'],
  ['Roads field team', 'roads.field@jansunwai.demo'], ['Drainage officer', 'drainage.officer@jansunwai.demo'],
  ['Admin', 'admin@jansunwai.demo'],
];

export default function Login() {
  const { t } = useI18n();
  const { login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(loc.state?.expired ? 'Your session expired. Please sign in again.' : '');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const u = await login(email.trim(), password);
      nav(homeFor(u.role), { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally { setBusy(false); }
  };

  return (
    <div className="grid g2" style={{ maxWidth: 940, margin: '0 auto' }}>
      <form className="card" onSubmit={submit}>
        <h1>{t('login')}</h1>
        <ErrorBox error={error} />
        <div className="field"><label htmlFor="email">{t('email')}</label>
          <input id="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required /></div>
        <div className="field"><label htmlFor="pw">{t('password')}</label>
          <input id="pw" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></div>
        <button disabled={busy}>{busy ? '…' : t('login')}</button>
        <p className="small">{t('noAccount')} <Link to="/register">{t('register')}</Link></p>
      </form>
      <div className="card">
        <h3>{t('demoAccounts')}</h3>
        <p className="muted small">Sample accounts created by the demo data. Click one to fill the e-mail.</p>
        <div className="stack">
          {DEMO.map(([role, mail]) => (
            <button key={mail} type="button" className="btn-secondary" style={{ width: '100%', justifyContent: 'space-between' }}
              onClick={() => setEmail(mail)}>
              <span>{role}</span><span className="small muted mono">{mail}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
