import { useAuth } from './AuthContext';

export const usePermissions = () => {
  const { user } = useAuth();
  return {
    canCreate:         user?.role === 'DPO',
    canViewAll:        user?.role === 'DPO',
    canViewDetail:     user?.role === 'DPO',
    canRevoke:         user?.role === 'TITULAR',
    canManageUsers:    user?.role === 'ADMIN',
    canViewMetrics:    user?.role === 'ADMIN' || user?.role === 'DPO',
    canViewAudit:      user?.role === 'ADMIN' || user?.role === 'DPO',
    canViewCompliance: user?.role === 'ADMIN' || user?.role === 'DPO',
    canViewTemplates:  user?.role === 'DPO',
    canUpdateProfile:  user !== null,
    isTitular:         user?.role === 'TITULAR',
    userArea:          user?.area ?? null,
    role:              user?.role ?? null,
  };
};
