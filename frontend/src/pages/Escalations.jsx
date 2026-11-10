import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../api.js';
import ComplaintTable from '../components/ComplaintTable.jsx';
import { Empty, ErrorBox, fmt, Loading, Pager } from '../components/ui.jsx';

export default function Escalations() {
  const [overdue, setOverdue] = useState(null);
  const [esc, setEsc] = useState(null);
  const [p, setP] = useState(0);
  const [error, setError] = useState('');
  useEffect(() => { api.get('/sla/overdue', { params: { size: 50 } }).then((r) => setOverdue(r.data)).catch((e) => setError(errorMessage(e))); }, []);
  useEffect(() => { api.get('/escalations', { params: { page: p, size: 15 } }).then((r) => setEsc(r.data)).catch((e) => setError(errorMessage(e))); }, [p]);
  return (
    <div className="stack">
      <div className="page-head"><div><h1>Overdue &amp; escalations</h1>
        <p className="muted">The SLA monitor runs every 5 minutes. Level 1 goes to the department officer at the deadline, level 2 to administrators 24 h later. Each level is recorded once.</p></div></div>
      <ErrorBox error={error} />
      <div className="card"><h2>Past the deadline now</h2>
        {!overdue ? <Loading /> : overdue.content.length === 0 ? <Empty>Nothing overdue. 🎉</Empty> : <ComplaintTable rows={overdue.content} staff />}</div>
      <div className="card"><h2>Escalation history</h2>
        {!esc ? <Loading /> : esc.content.length === 0 ? <Empty>No escalations yet.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Complaint</th><th>Level</th><th>Reason</th><th>Deadline was</th><th>Escalated</th></tr></thead>
            <tbody>{esc.content.map((e) => (
              <tr key={e.id}><td className="mono"><Link to={`/complaints/${e.complaintId}`}>{e.trackingId}</Link></td>
                <td><span className={`badge ${e.level === 2 ? 'b-red' : 'b-amber'}`}>L{e.level}</span></td><td>{e.reason}</td>
                <td className="small">{fmt(e.dueAt)}</td><td className="small">{fmt(e.createdAt)}</td></tr>))}</tbody>
          </table></div>)}
        <Pager page={esc} onPage={setP} />
      </div>
    </div>
  );
}
