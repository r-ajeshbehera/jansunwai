import { useEffect, useState } from 'react';
import { api, errorMessage } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useI18n } from '../i18n.jsx';
import ComplaintTable from '../components/ComplaintTable.jsx';
import { Empty, ErrorBox, Loading, Pager, STATUS_LABEL, STATUSES } from '../components/ui.jsx';

const PRESETS = {
  work: { title: 'myWork', tabs: [['ASSIGNED', 'To start'], ['IN_PROGRESS', 'In progress'], ['RESOLUTION_SUBMITTED', 'Waiting for approval'], ['', 'All']] },
  review: { title: 'review', tabs: [['UNDER_REVIEW', 'Needs review'], ['APPROVED', 'Accepted, not assigned'], ['RESOLUTION_SUBMITTED', 'Check resolution'], ['REOPENED', 'Reopened']] },
  all: { title: 'complaints', tabs: null },
};

export default function ComplaintList({ mode }) {
  const { t } = useI18n();
  const { user } = useAuth();
  const preset = PRESETS[mode];
  const [status, setStatus] = useState(preset.tabs ? preset.tabs[0][0] : '');
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [wardId, setWardId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [wards, setWards] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [p, setP] = useState(0);
  const [page, setPage] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (mode !== 'all') return;
    api.get('/wards').then((r) => setWards(r.data));
    if (user.role === 'ADMIN') api.get('/departments').then((r) => setDepartments(r.data));
  }, [mode, user.role]);

  useEffect(() => { setP(0); }, [status, query, wardId, departmentId, overdueOnly]);
  useEffect(() => {
    setPage(null);
    const params = { page: p, size: 20, status: status || undefined, q: query || undefined, wardId: wardId || undefined,
      departmentId: departmentId || undefined, overdueOnly: overdueOnly || undefined };
    api.get('/complaints', { params }).then((r) => setPage(r.data)).catch((e) => setError(errorMessage(e)));
  }, [p, status, query, wardId, departmentId, overdueOnly]);

  return (
    <div className="stack">
      <div className="page-head"><div><h1>{t(preset.title)}</h1>
        <p className="muted">{user.departmentName ? user.departmentName : user.role === 'ADMIN' ? 'All departments' : ''}</p></div></div>
      {preset.tabs && (
        <div className="tabs">{preset.tabs.map(([s, label]) => <button key={s} className={status === s ? 'on' : ''} onClick={() => setStatus(s)}>{label}</button>)}</div>
      )}
      {mode === 'all' && (
        <form className="card row" onSubmit={(e) => { e.preventDefault(); setQuery(q.trim()); }}>
          <input style={{ flex: 2, minWidth: 200 }} placeholder="Search title or tracking ID" value={q} onChange={(e) => setQ(e.target.value)} aria-label={t('search')} />
          <select style={{ flex: 1, minWidth: 150 }} value={status} onChange={(e) => setStatus(e.target.value)} aria-label={t('status')}>
            <option value="">{t('all')} statuses</option>{STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}</select>
          <select style={{ flex: 1, minWidth: 140 }} value={wardId} onChange={(e) => setWardId(e.target.value)} aria-label={t('ward')}>
            <option value="">{t('all')} wards</option>{wards.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</select>
          {departments.length > 0 && <select style={{ flex: 1, minWidth: 160 }} value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} aria-label={t('department')}>
            <option value="">{t('all')} departments</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select>}
          <label className="row small" style={{ margin: 0 }}><input type="checkbox" style={{ width: 'auto' }} checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)} /> {t('overdue')}</label>
          <button>{t('search')}</button>
        </form>
      )}
      <ErrorBox error={error} />
      <div className="card">
        {!page ? <Loading /> : page.content.length === 0 ? <Empty>{t('noData')}</Empty>
          : <><ComplaintTable rows={page.content} staff={user.role !== 'CITIZEN'} /><Pager page={page} onPage={setP} /></>}
      </div>
    </div>
  );
}
