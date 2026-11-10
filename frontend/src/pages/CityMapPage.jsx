import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useI18n } from '../i18n.jsx';
import { CityMap } from '../components/Maps.jsx';

export default function CityMapPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const [points, setPoints] = useState([]);
  const [hotspots, setHotspots] = useState([]);
  const staff = user && ['DEPARTMENT_OFFICER', 'ADMIN'].includes(user.role);

  useEffect(() => {
    if (!staff) return;
    api.get('/geo/heatmap').then((r) => setPoints(r.data)).catch(() => {});
    api.get('/geo/hotspots').then((r) => setHotspots(r.data)).catch(() => {});
  }, [staff]);

  return (
    <div className="stack">
      <div className="page-head"><div><h1>{t('map')}</h1>
        <p className="muted">Ward boundaries (demo wards drawn as rectangles). {staff ? 'Orange dots are open complaints; dashed circles are hotspots found with PostGIS clustering.' : 'Sign in as staff to see the complaint heatmap.'}</p></div></div>
      <CityMap points={points} hotspots={hotspots} tall />
    </div>
  );
}
