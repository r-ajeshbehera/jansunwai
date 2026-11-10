import { useEffect } from 'react';

export const STATUS_LABEL = {
  SUBMITTED: 'Submitted', AI_ANALYSIS_PENDING: 'Being analysed', UNDER_REVIEW: 'Under review', APPROVED: 'Accepted',
  REJECTED: 'Not accepted', ASSIGNED: 'Assigned', IN_PROGRESS: 'Work in progress', RESOLUTION_SUBMITTED: 'Work done, checking',
  RESOLVED: 'Resolved', CLOSED: 'Closed', REOPENED: 'Reopened',
};
const STATUS_CLASS = {
  SUBMITTED: 'b-grey', AI_ANALYSIS_PENDING: 'b-grey', UNDER_REVIEW: 'b-blue', APPROVED: 'b-teal', ASSIGNED: 'b-teal',
  IN_PROGRESS: 'b-amber', RESOLUTION_SUBMITTED: 'b-amber', RESOLVED: 'b-green', CLOSED: 'b-green', REJECTED: 'b-red', REOPENED: 'b-red',
};
export const CATEGORY_LABEL = {
  ROAD_DAMAGE: 'Road damage', WATER_SUPPLY: 'Water supply', DRAINAGE: 'Drainage & sewage', GARBAGE: 'Garbage',
  STREETLIGHT: 'Streetlight', SANITATION: 'Public sanitation', OTHER: 'Other',
};
export const CATEGORIES = Object.keys(CATEGORY_LABEL);
export const PRIORITIES = ['HIGH', 'MEDIUM', 'LOW'];
export const STATUSES = Object.keys(STATUS_LABEL);

export function StatusBadge({ status }) {
  return <span className={`badge ${STATUS_CLASS[status] || ''}`}>{STATUS_LABEL[status] || status}</span>;
}

export function PriorityBadge({ priority }) {
  if (!priority) return <span className="badge b-grey">not set</span>;
  const cls = { HIGH: 'b-red', MEDIUM: 'b-amber', LOW: 'b-grey' }[priority];
  return <span className={`badge ${cls}`}>{priority}</span>;
}

export function SlaBadge({ c }) {
  if (c.overdue) return <span className="badge b-red">Overdue{c.escalationLevel ? ` · L${c.escalationLevel}` : ''}</span>;
  if (c.escalationLevel) return <span className="badge b-red">Escalated L{c.escalationLevel}</span>;
  return null;
}

const dtf = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const df = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
export const fmt = (iso) => (iso ? dtf.format(new Date(iso)) : '—');
export const fmtDate = (iso) => (iso ? df.format(new Date(iso)) : '—');
export function ago(iso) {
  if (!iso) return '';
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 48) return `${h} h ago`;
  return `${Math.round(h / 24)} days ago`;
}
export function dueText(iso) {
  if (!iso) return '—';
  const h = Math.round((new Date(iso).getTime() - Date.now()) / 3600000);
  if (h < 0) return `${fmt(iso)} (${-h} h late)`;
  return `${fmt(iso)} (in ${h} h)`;
}

export const Loading = ({ text = 'Loading…' }) => <div className="empty">{text}</div>;
export const Empty = ({ children }) => <div className="empty">{children}</div>;
export const ErrorBox = ({ error }) => (error ? <div className="alert alert-error" role="alert">{error}</div> : null);
export const OkBox = ({ children }) => (children ? <div className="alert alert-ok" role="status">{children}</div> : null);

export function Pager({ page, onPage }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="pager">
      <button className="btn-secondary btn-sm" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>‹ Prev</button>
      <span>Page {page.page + 1} of {page.totalPages} · {page.totalElements} items</span>
      <button className="btn-secondary btn-sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>Next ›</button>
    </div>
  );
}

export function Modal({ title, children, onClose }) {
  useEffect(() => {
    const esc = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [onClose]);
  return (
    <div className="modal-bg" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function ScoreBar({ value }) {
  return <div className="score-bar" title={value}><span style={{ width: `${Math.round((value || 0) * 100)}%` }} /></div>;
}
