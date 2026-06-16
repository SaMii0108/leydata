import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../auth/mockUsers';

const ROLE_HOME: Record<Role, string> = {
  ADMIN:        '/',
  DPO:          '/',
  JEFE_DOMINIO: '/consentimientos',
  TITULAR:      '/titular/mis-consentimientos',
};

interface Props {
  roles?: Role[];
  fallback?: string;
  children: ReactNode;
}

const ProtectedRoute = ({ roles, fallback, children }: Props) => {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(user.role)) {
    const redirect = fallback ?? ROLE_HOME[user.role] ?? '/login';
    return <Navigate to={redirect} replace />;
  }
  return <>{children}</>;
};

export default ProtectedRoute;
