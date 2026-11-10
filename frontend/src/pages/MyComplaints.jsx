import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../api.js';
import { useI18n } from '../i18n.jsx';
import ComplaintTable from '../components/ComplaintTable.jsx';
import { Empty, ErrorBox, Loading, Pager } from '../components/ui.jsx';

export default function MyComplaints() {
  const { t } = useI18n();
  const [page, setPage] = useState(null);
  const [p, setP] = useState(0);
  const [error, setError] = useState('');
  useEffect(() => {
    api.get('/complaints', { params: { page: p, size: 15 } }).then((r) => setPage(r.data)).catch((e) => setError(errorMessage(e)));
  }, [p]);
  return (
    <div className="stack">
      <div className="page-head"><h1>{t('myComplaints')}</h1><Link className="btn" to="/new">+ {t('newComplaint')}</Link></div>
      <ErrorBox error={error} />
      <div className="card">
        {!page ? <Loading /> : page.content.length === 0 ? <Empty>{t('noData')} <Link to="/new">{t('reportNow')}</Link></Empty>
          : <><ComplaintTable rows={page.content} /><Pager page={page} onPage={setP} /></>}
      </div>
    </div>
  );
}
