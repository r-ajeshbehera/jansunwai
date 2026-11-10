import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api.js';
import { ErrorBox, fmt, Loading, Pager } from '../../components/ui.jsx';

export default function Audit() {
  const [page, setPage] = useState(null);
  const [p, setP] = useState(0);
  const [type, setType] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    api.get('/audit-logs', { params: { page: p, size: 30, entityType: type || undefined } }).then((r) => setPage(r.data)).catch((e) => setError(errorMessage(e)));
  }, [p, type]);
  return (
    <div className="stack">
      <div className="page-head"><div><h1>Audit log</h1><p className="muted">Who changed what. Written in the same transaction as the change.</p></div>
        <select style={{ width: 200 }} value={type} onChange={(e) => { setType(e.target.value); setP(0); }} aria-label="Entity type">
          <option value="">All entities</option>{['COMPLAINT', 'USER', 'DEPARTMENT', 'SLA_CONFIGURATION', 'DUPLICATE_CANDIDATE', 'WARD'].map((x) => <option key={x}>{x}</option>)}</select></div>
      <ErrorBox error={error} />
      <div className="card">{!page ? <Loading /> : (
        <div className="table-wrap"><table><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
          <tbody>{page.content.map((a) => <tr key={a.id}><td className="small">{fmt(a.createdAt)}</td><td>{a.actorId ?? 'system'}</td>
            <td><span className="badge">{a.action}</span></td><td className="small">{a.entityType} #{a.entityId}</td><td className="small mono">{a.details}</td></tr>)}</tbody></table></div>)}
        <Pager page={page} onPage={setP} /></div>
    </div>
  );
}
