import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { LANGS, useI18n } from '../i18n.jsx';

const MENUS = {
  GUEST: [['/', 'home'], ['/track', 'track'], ['/map', 'map']],
  CITIZEN: [['/my', 'myComplaints'], ['/new', 'newComplaint'], ['/track', 'track'], ['/map', 'map']],
  FIELD_OFFICER: [['/work', 'myWork']],
  DEPARTMENT_OFFICER: [['/desk', 'review'], ['/complaints', 'complaints'], ['/duplicates', 'duplicates'],
    ['/escalations', 'escalations'], ['/dashboard', 'dashboard'], ['/ward-reports', 'wardReports']],
  ADMIN: [['/dashboard', 'dashboard'], ['/complaints', 'complaints'], ['/desk', 'review'], ['/duplicates', 'duplicates'],
    ['/escalations', 'escalations'], ['/ward-reports', 'wardReports'], ['/admin/users', 'users'], ['/admin/sla', 'sla'],
    ['/admin/departments', 'departments'], ['/admin/ai', 'aiUsage'], ['/admin/audit', 'audit']],
};

export default function Layout() {
  const { user, logout } = useAuth();
  const { t, lang, setLang } = useI18n();
  const [unread, setUnread] = useState(0);
  const loc = useLocation();

  useEffect(() => {
    if (!user) return undefined;
    const load = () => api.get('/notifications/unread-count').then((r) => setUnread(r.data.count ?? r.data.unread ?? 0)).catch(() => {});
    load();
    const id = setInterval(load, 30000);
    return () => clearInterval(id);
  }, [user, loc.pathname]);

  const menu = MENUS[user?.role || 'GUEST'];
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            <img src="/favicon.svg" width="30" height="30" alt="" />
            <span>JanSunwai<small>{t('appTagline')}</small></span>
          </Link>
          <nav className="nav" aria-label="Main">
            {menu.map(([to, key]) => <NavLink key={to} to={to} end={to === '/'}>{t(key)}</NavLink>)}
          </nav>
          <div className="topbar-right">
            <select aria-label="Language" value={lang} onChange={(e) => setLang(e.target.value)}>
              {LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}
            </select>
            {user ? (
              <>
                <Link to="/notifications" className="bell" aria-label={`${t('notifications')}: ${unread} ${t('unread')}`}>
                  🔔{unread > 0 && <span className="count">{unread}</span>}
                </Link>
                <Link to="/profile" className="who">{user.fullName}<br /><span className="small">{user.role.replace('_', ' ').toLowerCase()}</span></Link>
                <button className="btn-secondary btn-sm" onClick={logout}>{t('logout')}</button>
              </>
            ) : (
              <>
                <Link className="btn btn-sm" to="/login">{t('login')}</Link>
                <Link className="btn btn-secondary btn-sm" to="/register">{t('register')}</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <div className="demo-banner">{t('demoBanner')}</div>
      <main><Outlet /></main>
      <footer>JanSunwai · portfolio project by Rajesh Behera · map data © OpenStreetMap contributors</footer>
    </>
  );
}
