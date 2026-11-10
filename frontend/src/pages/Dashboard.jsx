import { useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, errorMessage } from '../api.js';
import { useAuth } from '../auth.jsx';
import { CityMap } from '../components/Maps.jsx';
import { CATEGORY_LABEL, ErrorBox, Loading, STATUS_LABEL } from '../components/ui.jsx';

const COLORS = ['#0d9488', '#0f2a4a', '#f97316', '#6366f1', '#eab308', '#ec4899', '#64748b', '#22c55e', '#0ea5e9', '#a855f7', '#ef4444'];

function Stat({ label, value, warn }) {
  return <div className={`card stat ${warn ? 'warn' : ''}`}><div className="v">{value ?? '—'}</div><div className="l">{label}</div></div>;
}

const iso = (d) => d.toISOString().slice(0, 10);

export default function Dashboard() {
  const { user } = useAuth();
  const [from, setFrom] = useState(iso(new Date(Date.now() - 30 * 864e5)));
  const [to, setTo] = useState(iso(new Date()));
  const [d, setD] = useState(null);
  const [heat, setHeat] = useState([]);
  const [provider, setProvider] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setD(null);
    api.get('/dashboard/summary', { params: { from, to } }).then((r) => setD(r.data)).catch((e) => setError(errorMessage(e)));
  }, [from, to]);
  useEffect(() => {
    api.get('/geo/heatmap').then((r) => setHeat(r.data)).catch(() => {});
    api.get('/ai/provider').then((r) => setProvider(r.data)).catch(() => {});
  }, []);

  return (
    <div className="stack">
      <div className="page-head">
        <div><h1>Dashboard</h1><p className="muted">{d?.scope || (user.role === 'ADMIN' ? 'All departments' : user.departmentName)} · numbers are computed by SQL from the database</p></div>
        <div className="row">
          <div><label htmlFor="from">From</label><input id="from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></div>
          <div><label htmlFor="to">To</label><input id="to" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></div>
        </div>
      </div>
      <ErrorBox error={error} />
      {!d ? <Loading /> : (
        <>
          <div className="grid g4">
            <Stat label="Complaints received" value={d.totals.total} />
            <Stat label="Open (accepted, not resolved)" value={d.totals.pending} />
            <Stat label="Overdue now" value={d.totals.overdue} warn={d.totals.overdue > 0} />
            <Stat label="Avg. hours to resolve" value={d.totals.avgResolutionHours ?? '—'} />
          </div>
          <div className="grid g4">
            <Stat label="Waiting for review" value={d.totals.underReview} />
            <Stat label="Resolved / closed" value={d.totals.resolved} />
            <Stat label="Rejected" value={d.totals.rejected} />
            <Stat label="AI provider" value={provider ? (provider.mock ? 'Mock (offline)' : provider.provider) : '—'} />
          </div>
          <div className="grid g2">
            <div className="card"><h3>By category</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={d.byCategory.map((c) => ({ name: CATEGORY_LABEL[c.key] || c.label, count: c.count }))} layout="vertical" margin={{ left: 30 }}>
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} /><XAxis type="number" allowDecimals={false} /><YAxis type="category" dataKey="name" width={120} />
                  <Tooltip /><Bar dataKey="count" fill="#0d9488" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer></div>
            <div className="card"><h3>By status</h3>
              <ResponsiveContainer width="100%" height={260}>
                <PieChart><Pie data={d.byStatus.map((s) => ({ name: STATUS_LABEL[s.key] || s.key, value: s.count }))} dataKey="value" nameKey="name" innerRadius={55} outerRadius={95}>
                  {d.byStatus.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}</Pie><Tooltip /><Legend /></PieChart>
              </ResponsiveContainer></div>
          </div>
          <div className="card"><h3>Opened vs resolved per day</h3>
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={d.trend}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="date" /><YAxis allowDecimals={false} /><Tooltip /><Legend />
                <Line type="monotone" dataKey="opened" stroke="#0f2a4a" strokeWidth={2} /><Line type="monotone" dataKey="resolved" stroke="#0d9488" strokeWidth={2} /></LineChart>
            </ResponsiveContainer></div>
          <div className="grid g-side">
            <div className="card"><h3>Department performance</h3>
              <div className="table-wrap"><table>
                <thead><tr><th>Department</th><th>Total</th><th>Open</th><th>Resolved</th><th>Overdue</th><th>Avg h</th><th>On time</th></tr></thead>
                <tbody>{d.departments.map((x) => (
                  <tr key={x.departmentId}><td>{x.department}</td><td>{x.total}</td><td>{x.open}</td><td>{x.resolved}</td>
                    <td>{x.overdue > 0 ? <span className="badge b-red">{x.overdue}</span> : 0}</td><td>{x.avgResolutionHours ?? '—'}</td>
                    <td>{x.onTimePercent != null ? `${x.onTimePercent}%` : '—'}</td></tr>))}</tbody>
              </table></div></div>
            <div className="card"><h3>By ward</h3>
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={d.byWard.map((w) => ({ name: w.label, count: w.count }))}><XAxis dataKey="name" tick={{ fontSize: 11 }} /><YAxis allowDecimals={false} /><Tooltip />
                  <Bar dataKey="count" fill="#0f2a4a" radius={[4, 4, 0, 0]} /></BarChart>
              </ResponsiveContainer></div>
          </div>
          <div className="card"><h3>Open complaints map &amp; hotspots</h3>
            <p className="small muted">Hotspots: PostGIS ST_ClusterDBSCAN groups 2 or more open complaints within 250 m of each other.</p>
            <CityMap points={heat} hotspots={d.hotspots} /></div>
        </>
      )}
    </div>
  );
}
