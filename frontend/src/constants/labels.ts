import type { AuditAction } from '../utils/mockData';

export const ROLE_LABEL: Record<string, string> = {
  ADMIN:   'Administrador',
  DPO:     'DPO',
  USER:    'Usuario',
  TITULAR: 'Titular de datos',
};

export const ACTION_LABEL: Record<AuditAction, string> = {
  granted:  'Otorgado',
  revoked:  'Revocado',
  updated:  'Actualizado',
  viewed:   'Consultado',
  exported: 'Exportado',
  deleted:  'Eliminado',
};
