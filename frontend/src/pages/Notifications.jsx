import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../api.js';
import { useI18n } from '../i18n.jsx';
import { ago, Empty, ErrorBox, Loading, Pager } from '../components/ui.jsx';

export default function Notifications() {
  const { t } = useI18n();
  const [page, setPage] = useState(null);
  const [p, setP] = useState(0);
  const [error, setError] = useState('');
  const load = () => api.get('/notifications', { params: { page: p, size: 20 } }).then((r) => setPage(r.data)).catch((e) => setError(errorMessage(e)));
  useEffect(() => { load(); }, [p]);
  const read = async (id) => { await api.post(`/notifications/${id}/read`); load(); };
  return (
    <div className="stack">
      <div className="page-head"><div><h1>{t('notifications')}</h1><p className="muted small">In-app notifications. Real e-mail/SMS sending is out of scope for this demo.</p></div></div>
      <ErrorBox error={error} />
      <div className="card">
        {!page ? <Loading /> : page.content.length === 0 ? <Empty>{t('noData')}</Empty> : page.content.map((n) => (
          <div key={n.id} className="row between" style={{ borderBottom: '1px solid #eef2f7', padding: '10px 0', opacity: n.read ? 0.65 : 1 }}>
            <div><span className="badge b-teal">{n.type.replaceAll('_', ' ').toLowerCase()}</span> {!n.read && <b>●</b>} {n.message}
              <div className="small muted">{ago(n.createdAt)} {n.complaintId && <Link to={`/complaints/${n.complaintId}`}>open complaint</Link>}</div></div>
            {!n.read && <button className="btn-secondary btn-sm" onClick={() => read(n.id)}>{t('markRead')}</button>}
          </div>))}
        <Pager page={page} onPage={setP} />
      </div>
    </div>
  );
}
