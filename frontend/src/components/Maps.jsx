import { useEffect, useState } from 'react';
import { CircleMarker, GeoJSON, MapContainer, Marker, Popup, TileLayer, Tooltip, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import { api } from '../api.js';

// Vite bundles the marker images; tell Leaflet where they are.
L.Icon.Default.mergeOptions({ iconUrl: markerIcon, iconRetinaUrl: markerIcon2x, shadowUrl: markerShadow });

export const CITY_CENTER = [20.296, 85.83];       // Bhubaneswar (demo wards)
const OSM = { url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', attribution: '&copy; OpenStreetMap contributors' };

function useWardShapes() {
  const [shapes, setShapes] = useState([]);
  useEffect(() => { api.get('/wards/shapes').then((r) => setShapes(r.data)).catch(() => {}); }, []);
  return shapes;
}

function Wards({ shapes }) {
  return shapes.map((w) => (
    <GeoJSON key={w.id} data={JSON.parse(w.geoJson)} style={{ color: '#0f2a4a', weight: 1.5, fillColor: '#14b8a6', fillOpacity: 0.06 }}>
      <Tooltip sticky>{w.name}</Tooltip>
    </GeoJSON>
  ));
}

function ClickToPick({ onPick }) {
  useMapEvents({ click: (e) => onPick([Number(e.latlng.lat.toFixed(6)), Number(e.latlng.lng.toFixed(6))]) });
  return null;
}

/** Location picker for the complaint form (OpenStreetMap tiles, free). */
export function MapPicker({ value, onChange }) {
  const shapes = useWardShapes();
  return (
    <MapContainer center={value || CITY_CENTER} zoom={13} className="map" scrollWheelZoom>
      <TileLayer {...OSM} />
      <Wards shapes={shapes} />
      <ClickToPick onPick={onChange} />
      {value && <Marker position={value} />}
    </MapContainer>
  );
}

export function PointMap({ lat, lng, label }) {
  return (
    <MapContainer center={[lat, lng]} zoom={16} className="map" style={{ height: 240 }} scrollWheelZoom={false}>
      <TileLayer {...OSM} />
      <Marker position={[lat, lng]}>{label && <Popup>{label}</Popup>}</Marker>
    </MapContainer>
  );
}

/** Heat points (weight = number/priority of open complaints) and DBSCAN hotspots over the ward outlines. */
export function CityMap({ points = [], hotspots = [], tall }) {
  const shapes = useWardShapes();
  return (
    <MapContainer center={CITY_CENTER} zoom={12} className={`map ${tall ? 'tall' : ''}`} scrollWheelZoom>
      <TileLayer {...OSM} />
      <Wards shapes={shapes} />
      {points.map((p, i) => (
        <CircleMarker key={i} center={[p.lat, p.lng]} radius={5 + 3 * p.weight}
          pathOptions={{ color: '#c2410c', weight: 1, fillColor: '#f97316', fillOpacity: 0.45 }} />
      ))}
      {hotspots.map((h) => (
        <CircleMarker key={`h${h.clusterId}`} center={[h.lat, h.lng]} radius={14 + 2 * h.complaints}
          pathOptions={{ color: '#0f2a4a', weight: 2, dashArray: '4 4', fillOpacity: 0 }}>
          <Tooltip>Hotspot: {h.complaints} complaints · mostly {h.topCategory} · {h.wardName || 'outside wards'}</Tooltip>
        </CircleMarker>
      ))}
    </MapContainer>
  );
}
