import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../api.js';
import { CATEGORY_LABEL, Empty, ErrorBox, fmt, Loading, OkBox, ScoreBar, StatusBadge } from '../components/ui.jsx';

function Side({ c, label }) {
  return (
    <div className="card" style={{ boxShadow: 'none' }}>
      <div className="small muted">{label}</div>
      <div className="row"><Link className="mono" to={`/complaints/${c.id}`}>{c.trackingId}</Link><StatusBadge status={c.status} /></div>
      <h3 style={{ margin: '6px 0' }}>{c.title}</h3>
      <div className="small muted">{CATEGORY_LABEL[c.category] || 'no category yet'} · {c.wardName || 'no ward'} · {fmt(c.createdAt)}</div>
    </div>
  );
}

export default function Duplicates() {
  const [list, setList] = useState(null);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const load = () => api.get('/ai/duplicates/pending').then((r) => setList(r.data)).catch((e) => setError(errorMessage(e)));
  useEffect(() => { load(); }, []);
  const decide = async (id, what) => {
    setError(''); setOk('');
    try { await api.post(`/ai/duplicates/candidates/${id}/${what}`); setOk(what === 'approve' ? 'Linked. Both citizens keep their tracking IDs.' : 'Marked as different issues.'); load(); }
    catch (e) { setError(errorMessage(e)); }
  };
  return (
    <div className="stack">
      <div className="page-head"><div><h1>Possible duplicates</h1>
        <p className="muted">Found by meaning (pgvector embeddings), distance (PostGIS) and category. Works across languages. Nothing is merged until you decide.</p></div></div>
      <ErrorBox error={error} /><OkBox>{ok}</OkBox>
      {!list ? <Loading /> : list.length === 0 ? <div className="card"><Empty>No pending suggestions.</Empty></div> : list.map((d) => (
        <div key={d.id} className="card">
          <div className="dup-pair">
            <Side c={d.complaint} label="New complaint" />
            <div className="vs">≈<div className="small">{Math.round(d.totalScore * 100)}%</div></div>
            <Side c={d.candidate} label="Existing complaint (would become the master)" />
          </div>
          <div className="grid g3" style={{ marginTop: 12 }}>
            <div><div className="small muted">Meaning similarity {d.semanticScore}</div><ScoreBar value={d.semanticScore} /></div>
            <div><div className="small muted">Distance</div><b>{d.distanceMeters != null ? `${d.distanceMeters} m` : 'unknown'}</b></div>
            <div><div className="small muted">Total score (60% meaning, 25% distance, 15% category)</div><ScoreBar value={d.totalScore} /></div>
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <button onClick={() => decide(d.id, 'approve')}>Same issue, link them</button>
            <button className="btn-secondary" onClick={() => decide(d.id, 'reject')}>Different issues</button>
          </div>
        </div>
      ))}
    </div>
  );
}
