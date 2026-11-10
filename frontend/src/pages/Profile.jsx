import { useState } from 'react';
import { api, errorMessage } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useI18n } from '../i18n.jsx';
import { ErrorBox, OkBox } from '../components/ui.jsx';

export default function Profile() {
  const { t } = useI18n();
  const { user, setUser } = useAuth();
  const [f, setF] = useState({ fullName: user.fullName, phone: user.phone || '' });
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '' });
  const [msg, setMsg] = useState(''); const [error, setError] = useState('');
  const save = async (e) => {
    e.preventDefault(); setError(''); setMsg('');
    try { const r = await api.put('/users/me', { fullName: f.fullName, phone: f.phone || null }); setUser(r.data); setMsg('Profile saved.'); } catch (err) { setError(errorMessage(err)); }
  };
  const change = async (e) => {
    e.preventDefault(); setError(''); setMsg('');
    try { await api.patch('/users/me/password', pw); setPw({ currentPassword: '', newPassword: '' }); setMsg('Password changed.'); } catch (err) { setError(errorMessage(err)); }
  };
  return (
    <div className="stack" style={{ maxWidth: 640 }}>
      <h1>{t('profile')}</h1>
      <ErrorBox error={error} /><OkBox>{msg}</OkBox>
      <form className="card" onSubmit={save}>
        <p className="muted small">{user.email} · {user.role}{user.departmentName ? ` · ${user.departmentName}` : ''}</p>
        <div className="field"><label htmlFor="fn">{t('fullName')}</label><input id="fn" value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} required /></div>
        <div className="field"><label htmlFor="ph">{t('phone')}</label><input id="ph" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} pattern="[6-9][0-9]{9}" /></div>
        <button>{t('save')}</button>
      </form>
      <form className="card" onSubmit={change}>
        <h3>Change password</h3>
        <div className="field"><label htmlFor="cp">Current password</label><input id="cp" type="password" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} required /></div>
        <div className="field"><label htmlFor="np">New password</label><input id="np" type="password" minLength={8} value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} required /></div>
        <button>{t('save')}</button>
      </form>
    </div>
  );
}
