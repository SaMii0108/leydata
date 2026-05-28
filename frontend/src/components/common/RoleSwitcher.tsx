import { useState, useRef, useEffect } from 'react';
import type { Role } from '../../features/auth/mockUsers';
import { useAuth } from '../../features/auth/useAuth';
import { ROLE_LABEL } from '../../constants/labels';
import styles from './RoleSwitcher.module.css';

const ROLE_COLOR: Record<Role, string> = {
  ADMIN:        '#4361ee',
  DPO:          '#7c3aed',
  JEFE_DOMINIO: '#059669',
  TITULAR:      '#0891b2',
};

interface RoleSwitcherProps {
  /** 'sidebar' — texto claro sobre fondo oscuro | 'header' — sobre fondo blanco */
  variant?: 'sidebar' | 'header';
}

const RoleSwitcher = ({ variant = 'sidebar' }: RoleSwitcherProps) => {
  const { user, switchRole } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // Roles operativos disponibles (excluye TITULAR)
  const operationalRoles = (user?.roles ?? []).filter((r) => r !== 'TITULAR') as Role[];
  const isMultirol = operationalRoles.length > 1;

  // Cierra al hacer clic fuera
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  if (!user) return null;

  const currentRole = user.role as Role;
  const color = ROLE_COLOR[currentRole] ?? '#64748b';
  const label = ROLE_LABEL[currentRole] ?? currentRole;
  const domain = user.domains?.[0];

  // Usuario con un solo rol → texto estático
  if (!isMultirol) {
    return (
      <span className={[styles.roleText, styles[`role_${variant}`]].join(' ')} style={{ color }}>
        {label}{domain ? ` · ${domain}` : ''}
      </span>
    );
  }

  // Multirol → botón con dropdown
  return (
    <div className={styles.wrap} ref={ref}>
      <button
        className={[styles.trigger, styles[`trigger_${variant}`]].join(' ')}
        onClick={() => setOpen((v) => !v)}
        title="Cambiar perfil"
        aria-expanded={open}
        aria-haspopup="listbox"
      >
        <span style={{ color }}>{label}{domain ? ` · ${domain}` : ''}</span>
        <svg
          className={[styles.chevron, open ? styles.chevronOpen : ''].join(' ')}
          width="10" height="10" viewBox="0 0 24 24"
          fill="none" stroke="currentColor" strokeWidth="2.5"
          strokeLinecap="round" strokeLinejoin="round"
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>

      {open && (
        <div className={[styles.dropdown, styles[`dropdown_${variant}`]].join(' ')} role="listbox">
          <p className={styles.dropdownLabel}>Cambiar perfil</p>
          {operationalRoles.map((role) => (
            <button
              key={role}
              role="option"
              aria-selected={role === currentRole}
              className={[styles.option, role === currentRole ? styles.optionActive : ''].join(' ')}
              onClick={() => { switchRole(role); setOpen(false); }}
            >
              <span
                className={styles.optionDot}
                style={{ background: ROLE_COLOR[role] }}
              />
              <span className={styles.optionLabel}>{ROLE_LABEL[role] ?? role}</span>
              {role === currentRole && (
                <svg className={styles.checkIcon} width="12" height="12" viewBox="0 0 24 24"
                  fill="none" stroke="currentColor" strokeWidth="2.5"
                  strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default RoleSwitcher;
