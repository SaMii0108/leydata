/** Definición pura del contexto de autenticación — sin JSX ni componentes. */
import { createContext } from 'react';
import type { AppUser, Role } from './mockUsers';

export interface AuthContextValue {
  /** Usuario activo en sesión (null si no autenticado) */
  user: AppUser | null;
  /**
   * Usuario pendiente de selección de rol.
   * Se establece cuando el usuario tiene múltiples roles y aún
   * no ha elegido con cuál ingresar. Null en todos los demás casos.
   */
  pendingUser: AppUser | null;
  /** Inicia sesión directamente (usuario con un solo rol) */
  login: (user: AppUser) => void;
  /** Guarda usuario en espera de selección de rol (multirol) */
  setPendingUser: (user: AppUser) => void;
  /** Confirma el rol elegido y activa la sesión */
  selectRole: (role: Role) => void;
  /** Cierra sesión completa */
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
