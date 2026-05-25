import { useAuth } from './useAuth';

export const usePermissions = () => {
  const { user } = useAuth();
  return {
    /** DPO: revisa solicitudes de propósito */
    canReviewRequests:  user?.role === 'DPO',
    /** JEFE_DOMINIO: crea solicitudes de propósito para sus dominios */
    canCreateRequests:  user?.role === 'JEFE_DOMINIO',
    /** DPO: ve todas las solicitudes */
    canViewAllRequests: user?.role === 'DPO',
    /** TITULAR: puede revocar sus propios consentimientos */
    canRevoke:          user?.role === 'TITULAR',
    /** ADMIN: gestiona usuarios y dominios */
    canManageUsers:     user?.role === 'ADMIN',
    canManageDomains:   user?.role === 'ADMIN',
    /** ADMIN + DPO: métricas, auditoría, cumplimiento */
    canViewMetrics:     user?.role === 'ADMIN' || user?.role === 'DPO',
    canViewAudit:       user?.role === 'ADMIN' || user?.role === 'DPO',
    canViewCompliance:  user?.role === 'ADMIN' || user?.role === 'DPO',
    canViewTemplates:   user?.role === 'DPO',
    canUpdateProfile:   user !== null,
    isTitular:          user?.role === 'TITULAR',
    isJefeDominio:      user?.role === 'JEFE_DOMINIO',
    isDpo:              user?.role === 'DPO',
    isAdmin:            user?.role === 'ADMIN',
    /** Dominios asignados al usuario (solo aplica a JEFE_DOMINIO) */
    userDomains:        user?.domains ?? [],
    role:               user?.role ?? null,
  };
};
