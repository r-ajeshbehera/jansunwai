import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import { homeFor, useAuth } from './auth.jsx';
import { Loading } from './components/ui.jsx';
import Home from './pages/Home.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Track from './pages/Track.jsx';
import CityMapPage from './pages/CityMapPage.jsx';
import MyComplaints from './pages/MyComplaints.jsx';
import NewComplaint from './pages/NewComplaint.jsx';
import ComplaintDetail from './pages/ComplaintDetail.jsx';
import ComplaintList from './pages/ComplaintList.jsx';
import Duplicates from './pages/Duplicates.jsx';
import Escalations from './pages/Escalations.jsx';
import Dashboard from './pages/Dashboard.jsx';
import WardReports from './pages/WardReports.jsx';
import Notifications from './pages/Notifications.jsx';
import Profile from './pages/Profile.jsx';
import Users from './pages/admin/Users.jsx';
import Sla from './pages/admin/Sla.jsx';
import Departments from './pages/admin/Departments.jsx';
import AiUsage from './pages/admin/AiUsage.jsx';
import Audit from './pages/admin/Audit.jsx';

/** Hides pages from the wrong roles. The backend enforces the same rules; this only keeps the UI tidy. */
function Guard({ roles, children }) {
  const { user, ready } = useAuth();
  if (!ready) return <Loading />;
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user.role)} replace />;
  return children;
}

const STAFF = ['DEPARTMENT_OFFICER', 'ADMIN'];

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/track" element={<Track />} />
        <Route path="/track/:trackingId" element={<Track />} />
        <Route path="/map" element={<CityMapPage />} />
        <Route path="/my" element={<Guard roles={['CITIZEN']}><MyComplaints /></Guard>} />
        <Route path="/new" element={<Guard roles={['CITIZEN']}><NewComplaint /></Guard>} />
        <Route path="/complaints/:id" element={<Guard><ComplaintDetail /></Guard>} />
        <Route path="/work" element={<Guard roles={['FIELD_OFFICER']}><ComplaintList mode="work" /></Guard>} />
        <Route path="/desk" element={<Guard roles={STAFF}><ComplaintList mode="review" /></Guard>} />
        <Route path="/complaints" element={<Guard roles={STAFF}><ComplaintList mode="all" /></Guard>} />
        <Route path="/duplicates" element={<Guard roles={STAFF}><Duplicates /></Guard>} />
        <Route path="/escalations" element={<Guard roles={STAFF}><Escalations /></Guard>} />
        <Route path="/dashboard" element={<Guard roles={STAFF}><Dashboard /></Guard>} />
        <Route path="/ward-reports" element={<Guard roles={STAFF}><WardReports /></Guard>} />
        <Route path="/notifications" element={<Guard><Notifications /></Guard>} />
        <Route path="/profile" element={<Guard><Profile /></Guard>} />
        <Route path="/admin/users" element={<Guard roles={['ADMIN']}><Users /></Guard>} />
        <Route path="/admin/sla" element={<Guard roles={['ADMIN']}><Sla /></Guard>} />
        <Route path="/admin/departments" element={<Guard roles={['ADMIN']}><Departments /></Guard>} />
        <Route path="/admin/ai" element={<Guard roles={['ADMIN']}><AiUsage /></Guard>} />
        <Route path="/admin/audit" element={<Guard roles={['ADMIN']}><Audit /></Guard>} />
        <Route path="*" element={<div className="card"><h1>Page not found</h1></div>} />
      </Route>
    </Routes>
  );
}
