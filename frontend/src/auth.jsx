import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, setUnauthorizedHandler, tokenStore } from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);
  const navigate = useNavigate();

  const clear = useCallback(() => { tokenStore.clear(); setUser(null); }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => { clear(); navigate('/login', { state: { expired: true } }); });
    if (!tokenStore.get()) { setReady(true); return; }
    api.get('/auth/me').then((r) => setUser(r.data)).catch(clear).finally(() => setReady(true));
  }, [clear, navigate]);

  const login = async (email, password) => {
    const r = await api.post('/auth/login', { email, password });
    tokenStore.set(r.data.accessToken);
    setUser(r.data.user);
    return r.data.user;
  };

  const logout = async () => {
    try { await api.post('/auth/logout'); } catch { /* token may already be invalid */ }
    clear();
    navigate('/');
  };

  const value = useMemo(() => ({ user, ready, login, logout, setUser }), [user, ready]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

export const homeFor = (role) => ({
  CITIZEN: '/my', FIELD_OFFICER: '/work', DEPARTMENT_OFFICER: '/desk', ADMIN: '/dashboard',
}[role] || '/');
