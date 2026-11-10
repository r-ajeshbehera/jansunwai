import { fmt, STATUS_LABEL } from './ui.jsx';

export default function Timeline({ entries }) {
  if (!entries?.length) return <p className="muted">No history yet.</p>;
  return (
    <ul className="timeline">
      {entries.map((e, i) => (
        <li key={i} className={e.publicNote ? '' : 'private'}>
          <div><b>{e.fromStatus === e.toStatus ? 'Note' : STATUS_LABEL[e.toStatus] || e.toStatus}</b>
            {!e.publicNote && <span className="badge b-grey" style={{ marginLeft: 6 }}>internal</span>}</div>
          {e.note && <div>{e.note}</div>}
          <div className="when">{fmt(e.at)}{e.actor ? ` · ${e.actor}` : ''}</div>
        </li>
      ))}
    </ul>
  );
}
