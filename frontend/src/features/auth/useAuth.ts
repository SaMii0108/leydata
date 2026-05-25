import { useContext } from 'react';
import { AuthContext } from './authContextDef';
import type { AuthContextValue } from './authContextDef';

export const useAuth = (): AuthContextValue => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
