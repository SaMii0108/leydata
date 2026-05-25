import { useState } from 'react';
import type { ReactNode } from 'react';
import type { AppUser, Role } from './mockUsers';
import { AuthContext } from './authContextDef';

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser]                   = useState<AppUser | null>(null);
  const [pendingUser, setPendingUserState] = useState<AppUser | null>(null);

  const login = (u: AppUser) => {
    setUser(u);
    setPendingUserState(null);
  };

  const setPendingUser = (u: AppUser) => {
    setPendingUserState(u);
    setUser(null);
  };

  const selectRole = (role: Role) => {
    if (!pendingUser) return;
    setUser({ ...pendingUser, role });
    setPendingUserState(null);
  };

  const logout = () => {
    setUser(null);
    setPendingUserState(null);
  };

  return (
    <AuthContext.Provider value={{ user, pendingUser, login, setPendingUser, selectRole, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
