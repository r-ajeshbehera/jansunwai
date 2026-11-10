import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api.js';
import { ErrorBox, Loading, OkBox, PRIORITIES } from '../../components/ui.jsx';

export default function Sla() {
  const [list, setList] = useState(null);
  const [departments, setDepartments] = useState([]);
  const [f, setF] = useState({ departmentId: '', priority: 'HIGH', resolutionHours: 24, warningPercent: 75 });
  const [error, setError] = useState(''); const [ok, setOk] = useState('');
  const load = () => api.get('/sla/configurations').then((r) => setList(r.data)).catch((e) => setError(errorMessage(e)));
  useEffect(() => { load(); api.get('/departments').then((r) => setDepartments(r.data)); }, []);
  const save = async (e) => {
    e.preventDefault(); setError(''); setOk('');
    try {
      await api.post('/sla/configurations', { ...f, departmentId: f.departmentId ? Number(f.departmentId) : null,
        resolutionHours: Number(f.resolutionHours), warningPercent: Number(f.warningPercent), active: true });
      setOk('Saved. New deadlines use it; existing deadlines are not changed.'); load();
    } catch (err) { setError(errorMessage(err)); }
  };
  return (
    <div className="stack">
      <div className="page-head"><div><h1>SLA rules</h1>
        <p className="muted">Sample values for the demo, not official municipal standards. A department rule overrides the default for that priority.</p></div></div>
      <ErrorBox error={error} /><OkBox>{ok}</OkBox>
      <div className="grid g-side">
        <div className="card">{!list ? <Loading /> : (
          <table><thead><tr><th>Scope</th><th>Priority</th><th>Resolve within</th><th>Warn at</th></tr></thead>
            <tbody>{list.map((s) => <tr key={s.id} style={{ opacity: s.active ? 1 : 0.5 }}><td>{s.departmentName}</td><td>{s.priority}</td>
              <td>{s.resolutionHours} h{s.resolutionHours >= 48 ? ` (${(s.resolutionHours / 24).toFixed(1)} days)` : ''}</td><td>{s.warningPercent}%</td></tr>)}</tbody></table>)}</div>
        <form className="card" onSubmit={save}>
          <h3>Add or change a rule</h3>
          <div className="field"><label htmlFor="sd">Department</label><select id="sd" value={f.departmentId} onChange={(e) => setF({ ...f, departmentId: e.target.value })}>
            <option value="">Default (all departments)</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
          <div className="field"><label htmlFor="sp">Priority</label><select id="sp" value={f.priority} onChange={(e) => setF({ ...f, priority: e.target.value })}>{PRIORITIES.map((p) => <option key={p}>{p}</option>)}</select></div>
          <div className="field"><label htmlFor="sh">Resolution hours</label><input id="sh" type="number" min={1} max={2160} value={f.resolutionHours} onChange={(e) => setF({ ...f, resolutionHours: e.target.value })} /></div>
          <div className="field"><label htmlFor="sw">Warning at % of the time</label><input id="sw" type="number" min={10} max={95} value={f.warningPercent} onChange={(e) => setF({ ...f, warningPercent: e.target.value })} /></div>
          <button>Save</button>
        </form>
      </div>
    </div>
  );
}
