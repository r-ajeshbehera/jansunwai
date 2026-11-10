import { Link } from 'react-router-dom';
import { ago, CATEGORY_LABEL, dueText, PriorityBadge, SlaBadge, StatusBadge } from './ui.jsx';

export default function ComplaintTable({ rows, staff }) {
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Tracking ID</th><th>Complaint</th><th>Status</th><th>Category</th><th>Priority</th>
          {staff && <th>Ward</th>}<th>Resolve by</th><th>Submitted</th></tr></thead>
        <tbody>
          {rows.map((c) => (
            <tr key={c.id}>
              <td className="mono"><Link to={`/complaints/${c.id}`}>{c.trackingId}</Link></td>
              <td><Link to={`/complaints/${c.id}`}>{c.title}</Link>
                {c.linkedDuplicate && <div><span className="badge b-blue">linked duplicate</span></div>}</td>
              <td><StatusBadge status={c.status} /> <SlaBadge c={c} /></td>
              <td>{CATEGORY_LABEL[c.category] || <span className="muted">—</span>}
                {c.classificationSource === 'AI_SUGGESTED' && <div className="small muted">AI suggestion</div>}</td>
              <td><PriorityBadge priority={c.priority} /></td>
              {staff && <td>{c.wardName || '—'}</td>}
              <td className="small">{c.slaDueAt ? dueText(c.slaDueAt) : '—'}</td>
              <td className="small muted">{ago(c.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
