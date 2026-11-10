import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api.js';
import { ErrorBox, fmtDate, Loading, OkBox, Pager } from '../../components/ui.jsx';

const ROLES = ['CITIZEN', 'FIELD_OFFICER', 'DEPARTMENT_OFFICER', 'ADMIN'];
const blank = { fullName: '', email: '', phone: '', password: '', role: 'FIELD_OFFICER', departmentId: '' };

export default function Users() {
  const [page, setPage] = useState(null);
  const [p, setP] = useState(0);
  const [role, setRole] = useState('');
  const [q, setQ] = useState('');
  const [departments, setDepartments] = useState([]);
  const [f, setF] = useState(blank);
  const [error, setError] = useState(''); const [ok, setOk] = useState('');
  const load = () => api.get('/users', { params: { page: p, size: 20, role: role || undefined, q: q || undefined } }).then((r) => setPage(r.data)).catch((e) => setError(errorMessage(e)));
  useEffect(() => { load(); }, [p, role]);
  useEffect(() => { api.get('/departments').then((r) => setDepartments(r.data)); }, []);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const create = async (e) => {
    e.preventDefault(); setError(''); setOk('');
    try {
      await api.post('/users', { ...f, phone: f.phone || null, departmentId: f.departmentId ? Number(f.departmentId) : null });
      setOk(`Created ${f.email}.`); setF(blank); load();
    } catch (err) { setError(errorMessage(err)); }
  };
  const toggle = async (u) => { try { await api.patch(`/users/${u.id}`, { active: !u.active }); load(); } catch (err) { setError(errorMessage(err)); } };
  const needsDept = f.role === 'FIELD_OFFICER' || f.role === 'DEPARTMENT_OFFICER';
  return (
    <div className="stack">
      <h1>Users</h1>
      <ErrorBox error={error} /><OkBox>{ok}</OkBox>
      <div className="grid g-side">
        <div className="card">
          <form className="row" onSubmit={(e) => { e.preventDefault(); setP(0); load(); }} style={{ marginBottom: 10 }}>
            <input style={{ flex: 2 }} placeholder="Search name or e-mail" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search" />
            <select style={{ flex: 1 }} value={role} onChange={(e) => { setRole(e.target.value); setP(0); }} aria-label="Role"><option value="">All roles</option>{ROLES.map((r) => <option key={r}>{r}</option>)}</select>
            <button>Search</button>
          </form>
          {!page ? <Loading /> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Name</th><th>E-mail</th><th>Role</th><th>Department</th><th>Since</th><th /></tr></thead>
              <tbody>{page.content.map((u) => (
                <tr key={u.id} style={{ opacity: u.active ? 1 : 0.5 }}><td>{u.fullName}</td><td className="small">{u.email}</td>
                  <td><span className="badge">{u.role.replace('_', ' ')}</span></td><td className="small">{u.departmentName || '—'}</td>
                  <td className="small">{fmtDate(u.createdAt)}</td>
                  <td><button className="btn-secondary btn-sm" onClick={() => toggle(u)}>{u.active ? 'Deactivate' : 'Activate'}</button></td></tr>))}</tbody>
            </table></div>)}
          <Pager page={page} onPage={setP} />
        </div>
        <form className="card" onSubmit={create}>
          <h3>Create staff account</h3>
          <p className="small muted">Staff accounts are only created here; public sign-up always creates a citizen.</p>
          <div className="field"><label htmlFor="un">Full name</label><input id="un" value={f.fullName} onChange={set('fullName')} required /></div>
          <div className="field"><label htmlFor="ue">E-mail</label><input id="ue" type="email" value={f.email} onChange={set('email')} required /></div>
          <div className="field"><label htmlFor="up">Mobile (optional)</label><input id="up" value={f.phone} onChange={set('phone')} pattern="[6-9][0-9]{9}" /></div>
          <div className="field"><label htmlFor="upw">Initial password</label><input id="upw" type="password" minLength={8} value={f.password} onChange={set('password')} required /></div>
          <div className="field"><label htmlFor="ur">Role</label><select id="ur" value={f.role} onChange={set('role')}>{ROLES.map((r) => <option key={r}>{r}</option>)}</select></div>
          {needsDept && <div className="field"><label htmlFor="ud">Department</label>
            <select id="ud" value={f.departmentId} onChange={set('departmentId')} required><option value="">Choose…</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>}
          <button>Create</button>
        </form>
      </div>
    </div>
  );
}
