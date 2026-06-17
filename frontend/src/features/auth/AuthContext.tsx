import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import type { AppUser, Role } from './mockUsers';

// ── Configuración Keycloak ────────────────────────────────────────────────────
const KEYCLOAK_URL   = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180/realms/leydata';
const KEYCLOAK_TOKEN = `${KEYCLOAK_URL}/protocol/openid-connect/token`;
const CLIENT_ID      = 'leydata-frontend';
const REFRESH_MS     = 4.5 * 60 * 1000; // 4 min 30 s — antes de que expire el access_token (5 min)

const BUSINESS_ROLES: Role[] = ['ADMIN', 'DPO', 'JEFE_DOMINIO', 'TITULAR'];

interface TokenPayload {
  sub: string;
  name?: string;
  preferred_username?: string;
  email?: string;
  realm_access?: { roles: string[] };
}

/** Parsea el JWT de Keycloak y devuelve un AppUser, o null si el token es inválido. */
export function parseKeycloakToken(accessToken: string): AppUser | null {
  try {
    const payload: TokenPayload = JSON.parse(atob(accessToken.split('.')[1]));
    const roles = payload.realm_access?.roles ?? [];
    const role = roles.find((r) => BUSINESS_ROLES.includes(r as Role)) as Role | undefined;
    if (!role) return null;
    return {
      id:    payload.sub,
      name:  payload.name ?? payload.preferred_username ?? 'Usuario',
      email: payload.email ?? '',
      role,
      area:  null,
    };
  } catch {
    return null;
  }
}

// ── Contexto ──────────────────────────────────────────────────────────────────
interface AuthContextValue {
  user:        AppUser | null;
  accessToken: string | null;
  /** Usuario pendiente de selección de rol (multirol mock). Null en todos los demás casos. */
  pendingUser:    AppUser | null;
  /**
   * Almacena el usuario autenticado en el contexto.
   * - Login por Keycloak: pasar `tokens` para activar el auto-refresh.
   * - Login mock (usuario único): omitir `tokens`.
   * - Actualización de perfil: llamar sin `tokens` para solo actualizar datos del usuario.
   */
  login:          (user: AppUser, tokens?: { access: string; refresh: string }) => void;
  /** Guarda el usuario en espera de selección de rol (flujo multirol). */
  setPendingUser: (user: AppUser) => void;
  /** Confirma el rol elegido y activa la sesión. */
  selectRole:     (role: Role) => void;
  logout:         () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user,         setUser]         = useState<AppUser | null>(null);
  const [accessToken,  setAccessToken]  = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);
  const [pendingUser,  setPendingState] = useState<AppUser | null>(null);

  const logout = useCallback(() => {
    setUser(null);
    setAccessToken(null);
    setRefreshToken(null);
    setPendingState(null);
  }, []);

  const login = useCallback((
    incomingUser: AppUser,
    tokens?: { access: string; refresh: string },
  ) => {
    setUser(incomingUser);
    setPendingState(null);
    if (tokens) {
      setAccessToken(tokens.access);
      setRefreshToken(tokens.refresh);
    }
  }, []);

  const setPendingUser = useCallback((u: AppUser) => {
    setPendingState(u);
    setUser(null);
  }, []);

  const selectRole = useCallback((role: Role) => {
    setPendingState((prev) => {
      if (!prev) return null;
      setUser({ ...prev, role });
      return null;
    });
  }, []);

  // ── Auto-refresh del access_token ─────────────────────────────────────────
  useEffect(() => {
    if (!refreshToken) return;

    const refresh = async () => {
      try {
        const params = new URLSearchParams({
          grant_type:    'refresh_token',
          client_id:     CLIENT_ID,
          refresh_token: refreshToken,
        });
        const res = await fetch(KEYCLOAK_TOKEN, {
          method:  'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body:    params.toString(),
        });
        if (!res.ok) { logout(); return; }
        const data = await res.json();
        setAccessToken(data.access_token);
        setRefreshToken(data.refresh_token);
      } catch {
        logout();
      }
    };

    const id = setInterval(refresh, REFRESH_MS);
    return () => clearInterval(id);
  }, [refreshToken, logout]);

  return (
    <AuthContext.Provider value={{ user, accessToken, pendingUser, login, setPendingUser, selectRole, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
