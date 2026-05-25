import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/useAuth';
import type { Role } from '../auth/mockUsers';

interface Props {
  roles?: Role[];
  fallback?: string;
  children: ReactNode;
}

const ProtectedRoute = ({ roles, fallback = '/consentimientos', children }: Props) => {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={fallback} replace />;
  return <>{children}</>;
};

export default ProtectedRoute;
