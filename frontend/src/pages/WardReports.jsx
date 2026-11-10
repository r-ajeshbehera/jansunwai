import { useEffect, useState } from 'react';
import { api, errorMessage } from '../api.js';
import { useAuth } from '../auth.jsx';
import { CATEGORY_LABEL, Empty, ErrorBox, fmt, Loading, OkBox } from '../components/ui.jsx';

export default function WardReports() {
  const { user } = useAuth();
  const [list, setList] = useState(null);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);
  const load = () => api.get('/dashboard/weekly-summary').then((r) => setList(r.data)).catch((e) => setError(errorMessage(e)));
  useEffect(() => { load(); }, []);
  const generate = async () => {
    setBusy(true); setError('');
    try { const r = await api.post('/dashboard/weekly-summary/generate'); setOk(`Generated ${r.data.length} ward report(s).`); load(); }
    catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  };
  return (
    <div className="stack">
      <div className="page-head"><div><h1>Weekly ward reports</h1>
        <p className="muted">The numbers are computed in SQL first; the AI only writes sentences from those facts, so it cannot invent figures. Generated every Monday 06:10 IST.</p></div>
        {user.role === 'ADMIN' && <button onClick={generate} disabled={busy}>{busy ? 'Generating…' : 'Generate this week now'}</button>}</div>
      <ErrorBox error={error} /><OkBox>{ok}</OkBox>
      {!list ? <Loading /> : list.length === 0 ? <div className="card"><Empty>No reports for this week yet.</Empty></div> : (
        <div className="grid g2">{list.map((s) => (
          <div key={s.wardId} className="card">
            <div className="row between"><h2 style={{ margin: 0 }}>{s.facts.wardName}</h2>
              <span className={`badge ${s.aiGenerated ? 'b-teal' : 'b-grey'}`}>{s.aiGenerated ? `AI · ${s.provider}` : 'template (AI unavailable)'}</span></div>
            <p className="small muted">Week starting {s.weekStart} · generated {fmt(s.generatedAt)}</p>
            <div className="narrative">{s.narrative}</div>
            <div className="grid g4" style={{ marginTop: 12 }}>
              <div><b>{s.facts.received}</b><div className="small muted">received</div></div>
              <div><b>{s.facts.resolved}</b><div className="small muted">resolved</div></div>
              <div><b>{s.facts.open}</b><div className="small muted">open</div></div>
              <div><b style={{ color: s.facts.overdue ? 'var(--red)' : undefined }}>{s.facts.overdue}</b><div className="small muted">overdue</div></div>
            </div>
            <div className="row" style={{ marginTop: 10 }}>{Object.entries(s.facts.byCategory || {}).map(([k, v]) => <span key={k} className="badge">{CATEGORY_LABEL[k] || k}: {v}</span>)}</div>
          </div>))}</div>)}
    </div>
  );
}
