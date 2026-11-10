import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { homeFor, useAuth } from '../auth.jsx';
import { useI18n } from '../i18n.jsx';

export default function Home() {
  const { t } = useI18n();
  const { user } = useAuth();
  const [id, setId] = useState('');
  const nav = useNavigate();
  return (
    <div className="stack">
      <section className="hero">
        <h1>{t('heroTitle')}</h1>
        <p>{t('heroText')}</p>
        <div className="row" style={{ marginTop: 22 }}>
          {user ? <Link className="btn" to={homeFor(user.role)}>{t('dashboard')}</Link>
            : <Link className="btn" to="/login">{t('reportNow')}</Link>}
          <Link className="btn btn-secondary" to="/track">{t('trackNow')}</Link>
        </div>
      </section>
      <section className="grid g3">
        {[1, 2, 3].map((n) => (
          <div key={n} className="card">
            <div className="badge b-teal">{n}</div>
            <h3 style={{ marginTop: 10 }}>{t(`step${n}`)}</h3>
            <p className="muted">{t(`step${n}t`)}</p>
          </div>
        ))}
      </section>
      <section className="card">
        <h2>{t('track')}</h2>
        <form className="row" onSubmit={(e) => { e.preventDefault(); if (id.trim()) nav(`/track/${id.trim().toUpperCase()}`); }}>
          <input style={{ maxWidth: 320 }} value={id} onChange={(e) => setId(e.target.value)} placeholder="JS-2026-001001" aria-label={t('trackingId')} />
          <button>{t('search')}</button>
        </form>
        <p className="muted small">{t('trackHint')}</p>
      </section>
    </div>
  );
}
