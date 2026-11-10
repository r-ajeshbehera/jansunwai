import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, attachmentUrl, errorMessage } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useI18n } from '../i18n.jsx';
import Timeline from '../components/Timeline.jsx';
import { PointMap } from '../components/Maps.jsx';
import {
  CATEGORIES, CATEGORY_LABEL, dueText, ErrorBox, fmt, Loading, Modal, OkBox, PRIORITIES, PriorityBadge, ScoreBar,
  SlaBadge, StatusBadge,
} from '../components/ui.jsx';

function Photos({ complaintId, attachments }) {
  const [urls, setUrls] = useState({});
  useEffect(() => {
    let alive = true;
    const made = [];
    attachments.forEach((a) => attachmentUrl(complaintId, a.id).then((u) => { made.push(u); if (alive) setUrls((m) => ({ ...m, [a.id]: u })); }).catch(() => {}));
    return () => { alive = false; made.forEach(URL.revokeObjectURL); };
  }, [complaintId, attachments]);
  if (!attachments.length) return <p className="muted small">No photos.</p>;
  return (
    <div className="thumbs">
      {attachments.map((a) => (
        <figure key={a.id} style={{ margin: 0 }}>
          {urls[a.id] ? <a href={urls[a.id]} target="_blank" rel="noreferrer"><img src={urls[a.id]} alt={a.originalName} /></a>
            : <div className="thumbs-ph" style={{ width: 110, height: 82, background: '#eef2f7', borderRadius: 8 }} />}
          <figcaption className="small"><span className={`badge ${a.kind === 'RESOLUTION' ? 'b-green' : 'b-grey'}`}>{a.kind === 'RESOLUTION' ? 'work done' : 'citizen'}</span></figcaption>
        </figure>
      ))}
    </div>
  );
}

function ReviewPanel({ c, departments, onDone }) {
  const ai = c.aiAnalysis;
  const aiDept = departments.find((d) => d.code === ai?.departmentCode);
  const [f, setF] = useState({
    category: c.category || ai?.category || '', priority: c.priority || ai?.priority || 'MEDIUM',
    departmentId: c.departmentId || aiDept?.id || '', note: '',
  });
  const [modal, setModal] = useState(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const send = async (decision, extra = {}) => {
    setError('');
    try {
      const body = { decision, category: f.category || null, priority: f.priority || null,
        departmentId: f.departmentId ? Number(f.departmentId) : null, note: f.note || null, ...extra };
      await api.post(`/complaints/${c.id}/review`, body);
      setModal(null);
      onDone(decision === 'APPROVE' ? 'Accepted. The citizen has been told the deadline.' : decision === 'REJECT' ? 'Complaint rejected.' : 'Sent to the other department.');
    } catch (err) { setError(errorMessage(err)); }
  };
  return (
    <div className="card">
      <h3>Review</h3>
      <p className="small muted">Check the AI suggestion, correct it if needed, then accept. Nothing is routed without an officer's decision.</p>
      <ErrorBox error={error} />
      <div className="inline-fields">
        <div className="field"><label htmlFor="rc">{'Category'}</label>
          <select id="rc" value={f.category} onChange={set('category')}><option value="">—</option>
            {CATEGORIES.map((k) => <option key={k} value={k}>{CATEGORY_LABEL[k]}</option>)}</select></div>
        <div className="field"><label htmlFor="rp">Priority</label>
          <select id="rp" value={f.priority} onChange={set('priority')}>{PRIORITIES.map((p) => <option key={p}>{p}</option>)}</select></div>
      </div>
      <div className="field"><label htmlFor="rd">Department</label>
        <select id="rd" value={f.departmentId} onChange={set('departmentId')}><option value="">—</option>
          {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
      <div className="field"><label htmlFor="rn">Note for the citizen (optional)</label><input id="rn" value={f.note} onChange={set('note')} maxLength={500} /></div>
      <div className="row">
        <button onClick={() => send('APPROVE')}>Approve</button>
        <button className="btn-secondary" onClick={() => send('REROUTE')}>Send to selected department</button>
        <button className="btn-danger" onClick={() => setModal('reject')}>Reject</button>
      </div>
      {modal === 'reject' && (
        <Modal title="Reject complaint" onClose={() => setModal(null)}>
          <p className="small muted">The citizen sees this reason. The public tracking page only says "Not accepted".</p>
          <ErrorBox error={error} />
          <textarea aria-label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} placeholder="e.g. Private property, not maintained by the municipality" />
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn-danger" disabled={!reason.trim()} onClick={() => send('REJECT', { reason })}>Confirm</button>
            <button className="btn-secondary" onClick={() => setModal(null)}>Cancel</button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function AssignPanel({ c, reassign, onDone }) {
  const [officers, setOfficers] = useState([]);
  const [sel, setSel] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    api.get(`/departments/${c.departmentId}/field-officers`).then((r) => setOfficers(r.data)).catch((e) => setError(errorMessage(e)));
  }, [c.departmentId]);
  const go = async () => {
    setError('');
    try {
      await api.post(`/complaints/${c.id}/assign`, { fieldOfficerId: Number(sel), note: note || null, reassign });
      onDone(reassign ? 'Reassigned.' : 'Assigned to the field team.');
    } catch (err) { setError(errorMessage(err)); }
  };
  return (
    <div className="card">
      <h3>{reassign ? 'Reassign field officer' : 'Assign field officer'}</h3>
      <ErrorBox error={error} />
      <div className="field"><select aria-label="Field officer" value={sel} onChange={(e) => setSel(e.target.value)}>
        <option value="">Choose…</option>
        {officers.filter((o) => o.active && o.id !== c.assignment?.fieldOfficerId).map((o) => <option key={o.id} value={o.id}>{o.fullName}</option>)}</select></div>
      <div className="field"><input aria-label="Note" placeholder="Instructions (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={300} /></div>
      <button disabled={!sel} onClick={go}>{reassign ? 'Reassign' : 'Assign'}</button>
    </div>
  );
}

function DuplicatePanel({ c, onDone }) {
  const [list, setList] = useState(null);
  const [error, setError] = useState('');
  const detect = useCallback(async () => {
    setError('');
    try { setList((await api.post(`/ai/duplicates/${c.id}/detect`)).data); } catch (err) { setError(errorMessage(err)); }
  }, [c.id]);
  // Detection is idempotent (unique pair constraint), so it is safe to run when the panel opens: shows existing + new.
  useEffect(() => { detect(); }, [detect]);
  const decide = async (id, what) => {
    try { await api.post(`/ai/duplicates/candidates/${id}/${what}`); onDone(what === 'approve' ? 'Linked as duplicate.' : 'Suggestion rejected.'); } catch (err) { setError(errorMessage(err)); }
  };
  return (
    <div className="card">
      <div className="row between"><h3 style={{ margin: 0 }}>Possible duplicates</h3><button className="btn-secondary btn-sm" onClick={detect}>Check now</button></div>
      <ErrorBox error={error} />
      {list && list.length === 0 && <p className="muted small">No similar open complaint nearby.</p>}
      {list?.map((d) => (
        <div key={d.id} style={{ borderTop: '1px solid var(--line)', paddingTop: 10, marginTop: 10 }}>
          <div><Link to={`/complaints/${d.candidate.id}`} className="mono">{d.candidate.trackingId}</Link> {d.candidate.title}</div>
          <div className="small muted">score {d.totalScore} · meaning {d.semanticScore} · {d.distanceMeters != null ? `${d.distanceMeters} m` : 'distance unknown'}</div>
          <ScoreBar value={d.totalScore} />
          {d.status === 'PENDING' && <div className="row" style={{ marginTop: 8 }}>
            <button className="btn-sm" onClick={() => decide(d.id, 'approve')}>Same issue, link</button>
            <button className="btn-sm btn-secondary" onClick={() => decide(d.id, 'reject')}>Different</button></div>}
        </div>
      ))}
    </div>
  );
}

function NoteAction({ label, placeholder, required, danger, onSubmit }) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const go = async () => {
    setError('');
    try { await onSubmit(note.trim() || null); setOpen(false); setNote(''); } catch (err) { setError(errorMessage(err)); }
  };
  return (
    <>
      <button className={danger ? 'btn-danger' : ''} onClick={() => setOpen(true)}>{label}</button>
      {open && (
        <Modal title={label} onClose={() => setOpen(false)}>
          <ErrorBox error={error} />
          <textarea aria-label="Note" value={note} onChange={(e) => setNote(e.target.value)} placeholder={placeholder} maxLength={500} />
          <div className="row" style={{ marginTop: 10 }}>
            <button disabled={required && !note.trim()} onClick={go}>Confirm</button>
            <button className="btn-secondary" onClick={() => setOpen(false)}>Cancel</button>
          </div>
        </Modal>
      )}
    </>
  );
}

export default function ComplaintDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const { t } = useI18n();
  const [c, setC] = useState(null);
  const [departments, setDepartments] = useState([]);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [remark, setRemark] = useState({ text: '', visibility: 'PUBLIC', infoRequest: false });
  const staff = user.role === 'DEPARTMENT_OFFICER' || user.role === 'ADMIN';

  const load = useCallback(() => api.get(`/complaints/${id}`).then((r) => setC(r.data)).catch((e) => setError(errorMessage(e))), [id]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { if (staff) api.get('/departments').then((r) => setDepartments(r.data.filter((d) => d.active))); }, [staff]);
  // The AI runs in the background: refresh until it has finished.
  useEffect(() => {
    if (!c || !['SUBMITTED', 'AI_ANALYSIS_PENDING'].includes(c.status)) return undefined;
    const tm = setTimeout(load, 2000);
    return () => clearTimeout(tm);
  }, [c, load]);

  const done = (msg) => { setOk(msg); setError(''); load(); };
  const status = async (s, note) => { await api.patch(`/complaints/${id}/status`, { status: s, note }); done(`Status changed to ${s.replace('_', ' ').toLowerCase()}.`); };
  const upload = async (file) => {
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    try { await api.post(`/complaints/${id}/attachments`, fd); done('Photo uploaded.'); } catch (err) { setError(errorMessage(err)); }
  };
  const addRemark = async (e) => {
    e.preventDefault();
    try { await api.post(`/complaints/${id}/remarks`, remark); setRemark({ ...remark, text: '', infoRequest: false }); done('Remark added.'); } catch (err) { setError(errorMessage(err)); }
  };
  const rerunAi = async () => { try { await api.post(`/ai/classify/${id}`); done('AI analysis updated.'); } catch (err) { setError(errorMessage(err)); } };

  if (error && !c) return <ErrorBox error={error} />;
  if (!c) return <Loading />;
  const can = (a) => c.allowedActions.includes(a);
  const ai = c.aiAnalysis;

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <div className="row"><span className="mono muted">{c.trackingId}</span><StatusBadge status={c.status} /><SlaBadge c={c} /></div>
          <h1 style={{ marginTop: 6 }}>{c.title}</h1>
        </div>
        <div className="row">
          {can('START_WORK') && <button onClick={() => status('IN_PROGRESS').catch((e) => setError(errorMessage(e)))}>{t('startWork')}</button>}
          {can('SUBMIT_RESOLUTION') && <NoteAction label={t('submitResolution')} required placeholder="What was done?" onSubmit={(n) => status('RESOLUTION_SUBMITTED', n)} />}
          {can('APPROVE_RESOLUTION') && <button onClick={() => status('RESOLVED').catch((e) => setError(errorMessage(e)))}>{t('approveResolution')}</button>}
          {can('SEND_BACK') && <NoteAction label={t('sendBack')} required placeholder="What is still missing?" onSubmit={(n) => status('IN_PROGRESS', n)} />}
          {can('CONFIRM_CLOSE') && <button onClick={() => status('CLOSED').catch((e) => setError(errorMessage(e)))}>{t('confirmClose')}</button>}
          {can('REOPEN') && <NoteAction label={t('reopen')} danger required placeholder="Why is the problem not solved?" onSubmit={(n) => status('REOPENED', n)} />}
        </div>
      </div>
      <ErrorBox error={error} /><OkBox>{ok}</OkBox>

      <div className="grid g-side">
        <div className="stack">
          <div className="card">
            <p style={{ whiteSpace: 'pre-wrap', marginTop: 0 }}>{c.description}</p>
            <dl className="kv">
              <dt>{t('category')}</dt><dd>{CATEGORY_LABEL[c.category] || '—'} {c.classificationSource === 'AI_SUGGESTED' && <span className="badge b-blue">AI suggestion, not confirmed</span>}</dd>
              <dt>{t('priority')}</dt><dd><PriorityBadge priority={c.priority} /></dd>
              <dt>{t('department')}</dt><dd>{c.departmentName || '—'}</dd>
              <dt>{t('ward')}</dt><dd>{c.wardName || '—'}</dd>
              {c.landmark && <><dt>{t('landmark')}</dt><dd>{c.landmark}</dd></>}
              <dt>{t('created')}</dt><dd>{fmt(c.createdAt)}{c.citizenName ? ` · by ${c.citizenName}` : ''}</dd>
              <dt>{t('dueBy')}</dt><dd>{c.slaDueAt ? dueText(c.slaDueAt) : '—'}</dd>
              {c.assignment && <><dt>Field officer</dt><dd>{c.assignment.fieldOfficerName}{c.assignment.note ? ` · "${c.assignment.note}"` : ''}</dd></>}
              {c.rejectionReason && <><dt>Reason</dt><dd>{c.rejectionReason}</dd></>}
              {c.duplicate && <><dt>Duplicate</dt><dd>{c.duplicate.isMaster
                ? <>Master of {c.duplicate.linkedTrackingIds.length} linked complaint(s) {c.duplicate.linkedTrackingIds.join(', ')}</>
                : <>Linked to <span className="mono">{c.duplicate.masterTrackingId}</span>, resolved together</>}</dd></>}
            </dl>
          </div>
          {staff && ai && (
            <div className="ai-box">
              <div className="row between"><h3 style={{ margin: 0 }}>🤖 {t('aiSuggestion')}</h3>
                <span className="small muted">{ai.mock ? t('mockNotice') : `${ai.provider} · ${ai.model}`} · {ai.latencyMs} ms</span></div>
              {ai.status === 'FAILED' ? <p className="alert alert-error">AI failed ({ai.errorCode}). Classify manually.</p> : (
                <dl className="kv" style={{ marginTop: 10 }}>
                  <dt>Suggestion</dt><dd>{CATEGORY_LABEL[ai.category]} · {ai.departmentCode} · <PriorityBadge priority={ai.priority} /></dd>
                  <dt>{t('confidence')}</dt><dd style={{ maxWidth: 240 }}>{ai.confidence} <ScoreBar value={Number(ai.confidence)} /></dd>
                  <dt>Language</dt><dd>{ai.detectedLanguage}</dd>
                  <dt>Summary (EN)</dt><dd>{ai.summaryEn}</dd>
                  {ai.translationEn && <><dt>Translation</dt><dd>{ai.translationEn}</dd></>}
                  {ai.suggestedActions && <><dt>Suggested action</dt><dd>{ai.suggestedActions}</dd></>}
                </dl>)}
              {can('RERUN_AI') && <button className="btn-secondary btn-sm" onClick={rerunAi}>Run AI again</button>}
            </div>
          )}
          {can('REVIEW') && <ReviewPanel c={c} departments={departments} onDone={done} />}
          <div className="card">
            <div className="row between"><h3 style={{ margin: 0 }}>{t('photos')}</h3>
              {(can('ADD_PHOTO') || can('UPLOAD_RESOLUTION_PHOTO')) && (
                <label className="btn btn-secondary btn-sm" style={{ margin: 0 }}>
                  {can('UPLOAD_RESOLUTION_PHOTO') ? 'Upload photo of the work' : t('uploadPhoto')}
                  <input type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(e) => upload(e.target.files[0])} />
                </label>)}
            </div>
            <div style={{ marginTop: 10 }}><Photos complaintId={c.id} attachments={c.attachments} /></div>
          </div>
          <div className="card">
            <h3>{t('remarks')}</h3>
            {c.remarks.length === 0 && <p className="muted small">No remarks yet.</p>}
            {c.remarks.map((r) => (
              <div key={r.id} style={{ borderBottom: '1px solid #eef2f7', padding: '8px 0' }}>
                <div className="small"><b>{r.author}</b> · {fmt(r.createdAt)} {r.visibility === 'INTERNAL' && <span className="badge b-grey">internal</span>}
                  {r.infoRequest && <span className="badge b-amber">information requested</span>}</div>
                <div>{r.text}</div>
              </div>
            ))}
            {can('ADD_REMARK') && (
              <form onSubmit={addRemark} style={{ marginTop: 10 }}>
                <textarea aria-label={t('addRemark')} style={{ minHeight: 70 }} value={remark.text} onChange={(e) => setRemark({ ...remark, text: e.target.value })} maxLength={1000} required />
                <div className="row" style={{ marginTop: 8 }}>
                  {user.role !== 'CITIZEN' && <>
                    <select style={{ width: 'auto' }} aria-label="Visibility" value={remark.visibility} onChange={(e) => setRemark({ ...remark, visibility: e.target.value })}>
                      <option value="PUBLIC">Visible to citizen</option><option value="INTERNAL">Internal only</option></select>
                    <label className="row small" style={{ margin: 0, fontWeight: 400 }}><input type="checkbox" style={{ width: 'auto' }} checked={remark.infoRequest}
                      onChange={(e) => setRemark({ ...remark, infoRequest: e.target.checked })} /> Ask the citizen for information</label>
                  </>}
                  <button>{t('addRemark')}</button>
                </div>
              </form>)}
          </div>
        </div>
        <div className="stack">
          {(can('ASSIGN') || can('REASSIGN')) && <AssignPanel c={c} reassign={can('REASSIGN')} onDone={done} />}
          {can('DETECT_DUPLICATES') && <DuplicatePanel c={c} onDone={done} />}
          {c.latitude != null && <div className="card"><h3>{t('location')}</h3><PointMap lat={Number(c.latitude)} lng={Number(c.longitude)} label={c.title} /></div>}
          <div className="card"><h3>{t('timeline')}</h3><Timeline entries={c.timeline} /></div>
        </div>
      </div>
    </div>
  );
}
