import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../api.js';
import { useI18n } from '../i18n.jsx';
import { ErrorBox, fmt, fmtDate, Loading, StatusBadge } from '../components/ui.jsx';

export default function Track() {
  const { t } = useI18n();
  const { trackingId } = useParams();
  const nav = useNavigate();
  const [id, setId] = useState(trackingId || '');
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!trackingId) return;
    setLoading(true); setError(''); setData(null);
    api.get(`/public/track/${encodeURIComponent(trackingId)}`)
      .then((r) => setData(r.data))
      .catch((e) => setError(e.response?.status === 404 ? 'No complaint with this tracking ID.' : errorMessage(e)))
      .finally(() => setLoading(false));
  }, [trackingId]);

  return (
    <div className="stack" style={{ maxWidth: 820, margin: '0 auto' }}>
      <div className="card">
        <h1>{t('track')}</h1>
        <form className="row" onSubmit={(e) => { e.preventDefault(); if (id.trim()) nav(`/track/${id.trim().toUpperCase()}`); }}>
          <input style={{ maxWidth: 320 }} value={id} onChange={(e) => setId(e.target.value)} placeholder="JS-2026-001001" aria-label={t('trackingId')} />
          <button>{t('search')}</button>
        </form>
        <p className="muted small">{t('trackHint')}</p>
      </div>
      {loading && <Loading />}
      <ErrorBox error={error} />
      {data && (
        <div className="card">
          <div className="row between">
            <h2 className="mono">{data.trackingId}</h2>
            <StatusBadge status={data.status} />
          </div>
          <dl className="kv">
            <dt>{t('category')}</dt><dd>{data.category || '—'}</dd>
            <dt>{t('department')}</dt><dd>{data.departmentName || '—'}</dd>
            <dt>{t('ward')}</dt><dd>{data.wardName || '—'}</dd>
            <dt>{t('created')}</dt><dd>{fmtDate(data.submittedAt)}</dd>
            <dt>{t('expectedBy')}</dt><dd>{data.expectedBy ? fmt(data.expectedBy) : '—'} {data.overdue && <span className="badge b-red">{t('overdue')}</span>}</dd>
            {data.linkedToTrackingId && <><dt>Linked to</dt><dd className="mono">{data.linkedToTrackingId} (same issue, resolved together)</dd></>}
          </dl>
          <h3 style={{ marginTop: 18 }}>{t('timeline')}</h3>
          <ul className="timeline">
            {data.history.map((h, i) => <li key={i}><b><StatusBadge status={h.status} /></b><div>{h.note}</div><div className="when">{fmt(h.at)}</div></li>)}
          </ul>
          <p className="muted small">{t('publicNote')}</p>
        </div>
      )}
    </div>
  );
}
