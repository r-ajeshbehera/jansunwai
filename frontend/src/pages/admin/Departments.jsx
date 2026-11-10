import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api.js';
import { ErrorBox, OkBox } from '../../components/ui.jsx';

export default function Departments() {
  const [depts, setDepts] = useState([]);
  const [wards, setWards] = useState([]);
  const [f, setF] = useState({ code: '', name: '' });
  const [error, setError] = useState(''); const [ok, setOk] = useState('');
  const load = () => { api.get('/departments').then((r) => setDepts(r.data)); api.get('/wards').then((r) => setWards(r.data)); };
  useEffect(load, []);
  const create = async (e) => {
    e.preventDefault(); setError(''); setOk('');
    try { await api.post('/departments', { ...f, code: f.code.toUpperCase(), active: true }); setOk('Department created.'); setF({ code: '', name: '' }); load(); }
    catch (err) { setError(errorMessage(err)); }
  };
  const toggle = async (d) => { try { await api.put(`/departments/${d.id}`, { code: d.code, name: d.name, active: !d.active }); load(); } catch (err) { setError(errorMessage(err)); } };
  return (
    <div className="stack">
      <h1>Departments &amp; wards</h1>
      <ErrorBox error={error} /><OkBox>{ok}</OkBox>
      <div className="grid g-side">
        <div className="card"><table><thead><tr><th>Code</th><th>Name</th><th>Status</th><th /></tr></thead>
          <tbody>{depts.map((d) => <tr key={d.id}><td className="mono">{d.code}</td><td>{d.name}</td>
            <td>{d.active ? <span className="badge b-green">active</span> : <span className="badge b-grey">inactive</span>}</td>
            <td><button className="btn-secondary btn-sm" onClick={() => toggle(d)}>{d.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody></table></div>
        <form className="card" onSubmit={create}>
          <h3>New department</h3>
          <div className="field"><label htmlFor="dc">Code</label><input id="dc" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} pattern="[A-Za-z_]{3,30}" required placeholder="PARKS" /></div>
          <div className="field"><label htmlFor="dn">Name</label><input id="dn" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} required /></div>
          <button>Create</button>
        </form>
      </div>
      <div className="card"><h3>Wards</h3>
        <p className="small muted">Demo wards are rectangles. Real ward boundaries come from the municipality as GeoJSON and are added through POST /api/wards.</p>
        <div className="row">{wards.map((w) => <span key={w.id} className="badge b-teal">{w.code} · {w.name}</span>)}</div></div>
    </div>
  );
}
