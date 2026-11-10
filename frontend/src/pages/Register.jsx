import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useI18n } from '../i18n.jsx';
import { ErrorBox } from '../components/ui.jsx';

export default function Register() {
  const { t } = useI18n();
  const { login } = useAuth();
  const nav = useNavigate();
  const [f, setF] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await api.post('/auth/register', f);
      await login(f.email, f.password);
      nav('/new');
    } catch (err) { setError(errorMessage(err)); } finally { setBusy(false); }
  };

  return (
    <form className="card" style={{ maxWidth: 520, margin: '0 auto' }} onSubmit={submit}>
      <h1>{t('register')}</h1>
      <p className="muted small">Citizen accounts only. Officer accounts are created by the municipality's administrator.</p>
      <ErrorBox error={error} />
      <div className="field"><label htmlFor="n">{t('fullName')}</label><input id="n" value={f.fullName} onChange={set('fullName')} required maxLength={120} /></div>
      <div className="field"><label htmlFor="e">{t('email')}</label><input id="e" type="email" value={f.email} onChange={set('email')} required /></div>
      <div className="field"><label htmlFor="p">{t('phone')}</label>
        <input id="p" value={f.phone} onChange={set('phone')} required pattern="[6-9][0-9]{9}" title="10-digit Indian mobile number" inputMode="numeric" /></div>
      <div className="field"><label htmlFor="pw">{t('password')}</label>
        <input id="pw" type="password" value={f.password} onChange={set('password')} required minLength={8} autoComplete="new-password" />
        <span className="small muted">At least 8 characters.</span></div>
      <button disabled={busy}>{t('register')}</button>
      <p className="small">{t('haveAccount')} <Link to="/login">{t('login')}</Link></p>
    </form>
  );
}
