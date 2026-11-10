import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../api.js';
import { LANGS, useI18n } from '../i18n.jsx';
import { MapPicker } from '../components/Maps.jsx';
import { CATEGORY_LABEL, ErrorBox } from '../components/ui.jsx';

export default function NewComplaint() {
  const { t, lang } = useI18n();
  const [f, setF] = useState({ title: '', description: '', language: lang, landmark: '' });
  const [pos, setPos] = useState(null);
  const [photo, setPhoto] = useState(null);
  const [nearby, setNearby] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  // Show what is already reported nearby, so people can see their problem may be known (fewer duplicates).
  useEffect(() => {
    if (!pos) { setNearby([]); return; }
    api.get('/geo/nearby-summary', { params: { lat: pos[0], lng: pos[1], radiusMeters: 250 } })
      .then((r) => setNearby(r.data)).catch(() => setNearby([]));
  }, [pos]);

  const locate = () => {
    if (!navigator.geolocation) { setError('Your browser cannot share location. Click the map instead.'); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => setPos([Number(p.coords.latitude.toFixed(6)), Number(p.coords.longitude.toFixed(6))]),
      () => setError('Location permission denied. Click the map instead.'),
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const body = { ...f, landmark: f.landmark || null, ...(pos ? { latitude: pos[0], longitude: pos[1] } : {}) };
      const r = await api.post('/complaints', body);
      let photoError = '';
      if (photo) {
        const fd = new FormData();
        fd.append('file', photo);
        try { await api.post(`/complaints/${r.data.id}/attachments`, fd); } catch (err) { photoError = errorMessage(err); }
      }
      setDone({ ...r.data, photoError });
    } catch (err) { setError(errorMessage(err)); } finally { setBusy(false); }
  };

  if (done) {
    return (
      <div className="card" style={{ maxWidth: 640, margin: '0 auto', textAlign: 'center' }}>
        <h1>✅ {t('submitted')}</h1>
        <p>{t('yourTrackingId')}</p>
        <p className="mono" style={{ fontSize: 26, fontWeight: 700, color: 'var(--navy)' }}>{done.trackingId}</p>
        {done.wardName && <p className="muted">{t('ward')}: {done.wardName}</p>}
        {done.photoError && <div className="alert alert-error">Photo not uploaded: {done.photoError}</div>}
        <p className="muted small">AI is analysing your complaint now; an officer will review it.</p>
        <div className="row" style={{ justifyContent: 'center' }}>
          <Link className="btn" to={`/complaints/${done.id}`}>View complaint</Link>
          <button className="btn-secondary" onClick={() => { setDone(null); setF({ ...f, title: '', description: '', landmark: '' }); setPos(null); setPhoto(null); }}>
            {t('newComplaint')}</button>
        </div>
      </div>
    );
  }

  return (
    <form className="grid g-side" onSubmit={submit}>
      <div className="card">
        <h1>{t('newComplaint')}</h1>
        <ErrorBox error={error} />
        <div className="field"><label htmlFor="title">{t('title')}</label>
          <input id="title" value={f.title} onChange={set('title')} required minLength={5} maxLength={150} placeholder="e.g. Big pothole near the market" /></div>
        <div className="field"><label htmlFor="desc">{t('description')}</label>
          <textarea id="desc" value={f.description} onChange={set('description')} required minLength={10} maxLength={4000}
            placeholder="What is the problem, since when, is anyone in danger?" />
          <span className="small muted">{f.description.length}/4000 · Do not write phone numbers or ID numbers here.</span></div>
        <div className="inline-fields">
          <div className="field"><label htmlFor="lang">{t('language')}</label>
            <select id="lang" value={f.language} onChange={set('language')}>{LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}</select></div>
          <div className="field"><label htmlFor="lm">{t('landmark')}</label><input id="lm" value={f.landmark} onChange={set('landmark')} maxLength={200} /></div>
        </div>
        <div className="field"><label htmlFor="photo">{t('optionalPhoto')}</label>
          <input id="photo" type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setPhoto(e.target.files[0] || null)} /></div>
        <button disabled={busy}>{busy ? '…' : t('submit')}</button>
      </div>
      <div className="card">
        <div className="row between"><h3 style={{ margin: 0 }}>{t('location')}</h3>
          <button type="button" className="btn-secondary btn-sm" onClick={locate}>📍 {t('useMyLocation')}</button></div>
        <p className="small muted">{t('clickMap')}</p>
        <MapPicker value={pos} onChange={setPos} />
        <p className="small mono">{pos ? `${pos[0]}, ${pos[1]}` : 'No location (allowed, but the complaint cannot be mapped)'}</p>
        {nearby.length > 0 && (
          <div className="alert alert-info">
            <b>Already reported within 250 m:</b>
            <ul style={{ margin: '6px 0 0 18px', padding: 0 }}>
              {nearby.map((n) => <li key={n.category}>{n.count} open {CATEGORY_LABEL[n.category] || n.category} complaint(s)</li>)}
            </ul>
            <span className="small">You can still submit. Similar complaints are linked by an officer, and you keep your own tracking ID.</span>
          </div>
        )}
      </div>
    </form>
  );
}
