import axios from 'axios';

// One Axios instance for the whole app. The JWT lives in sessionStorage (cleared when the tab closes).
const TOKEN_KEY = 'jansunwai.token';
export const tokenStore = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (t) => sessionStorage.setItem(TOKEN_KEY, t),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
};

export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/api', timeout: 20000 });

api.interceptors.request.use((config) => {
  const t = tokenStore.get();
  if (t) config.headers.Authorization = `Bearer ${t}`;
  return config;
});

let onUnauthorized = () => {};
export const setUnauthorizedHandler = (fn) => { onUnauthorized = fn; };

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401 && tokenStore.get() && !err.config.url.includes('/auth/login')) onUnauthorized();
    return Promise.reject(err);
  },
);

/** Human-readable message from the backend's ApiError ({code, message, fieldErrors}). */
export function errorMessage(err) {
  const d = err?.response?.data;
  if (d?.fieldErrors && Object.keys(d.fieldErrors).length) {
    return Object.entries(d.fieldErrors).map(([f, m]) => `${f}: ${m}`).join('; ');
  }
  if (d?.message) return d.message;
  if (err?.code === 'ECONNABORTED') return 'The server took too long to answer. Please try again.';
  if (!err?.response) return 'Cannot reach the server. Is the backend running?';
  return `Request failed (${err.response.status})`;
}

/** Attachments need the Authorization header, so they are fetched as a blob and shown via an object URL. */
export async function attachmentUrl(complaintId, attachmentId) {
  const r = await api.get(`/complaints/${complaintId}/attachments/${attachmentId}`, { responseType: 'blob' });
  return URL.createObjectURL(r.data);
}
