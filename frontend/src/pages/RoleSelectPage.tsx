import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/useAuth';
import type { Role } from '../features/auth/mockUsers';
import styles from './RoleSelectPage.module.css';

/* ─── Metadatos por rol ─────────────────────────────────────────────────────── */
interface RoleMeta {
  label: string;
  description: string;
  icon: React.ReactElement;
  color: string;
}

const IconShieldFill = () => (
  <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 2L3 6v6c0 5.25 3.75 10.15 9 11.25C17.25 22.15 21 17.25 21 12V6L12 2z"/>
  </svg>
);

const IconEye = () => (
  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
    <circle cx="12" cy="12" r="3"/>
  </svg>
);

const IconUserCircle = () => (
  <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
  </svg>
);

const ROLE_META: Record<Role, RoleMeta> = {
  ADMIN: {
    label:       'Administrador',
    description: 'Gestión de usuarios, dominios y configuración general del sistema.',
    icon:        <IconShieldFill />,
    color:       '#4361ee',
  },
  DPO: {
    label:       'DPO',
    description: 'Supervisión de consentimientos, auditoría y cumplimiento legal.',
    icon:        <IconEye />,
    color:       '#7c3aed',
  },
  USER: {
    label:       'Jefe de Área',
    description: 'Gestión de consentimientos y solicitudes de su área asignada.',
    icon:        <IconUserCircle />,
    color:       '#059669',
  },
  TITULAR: {
    label:       'Titular',
    description: 'Acceso al portal de titulares de datos.',
    icon:        <IconUserCircle />,
    color:       '#0891b2',
  },
};

/* ─── Destino por rol ──────────────────────────────────────────────────────── */
const destinationFor = (role: Role): string => {
  if (role === 'USER') return '/consentimientos';
  return '/';
};

/* ─── Componente principal ─────────────────────────────────────────────────── */
const RoleSelectPage = () => {
  const { pendingUser, selectRole, user } = useAuth();
  const navigate = useNavigate();

  // Guard: solo redirigir a login si NO hay ni usuario pendiente NI usuario activo.
  // Cuando se elige un rol, pendingUser pasa a null pero user se activa —
  // en ese momento NO se debe redirigir (la navegación al dashboard ya ocurrió).
  useEffect(() => {
    if (!pendingUser && !user) navigate('/login', { replace: true });
  }, [pendingUser, user, navigate]);

  if (!pendingUser) return null;

  const operationalRoles = pendingUser.roles.filter((r) => r !== 'TITULAR');

  const handleSelect = (role: Role) => {
    selectRole(role);
    navigate(destinationFor(role), { replace: true });
  };

  return (
    <div className={styles.page}>
      <div className={styles.container}>
        {/* Header */}
        <div className={styles.header}>
          <span className={styles.brand}>Ley Data</span>
          <h1 className={styles.title}>¿Con qué rol deseas ingresar?</h1>
          <p className={styles.subtitle}>
            Hola, <strong>{pendingUser.name}</strong>. Tu cuenta tiene múltiples roles asignados.
            Elige el perfil con el que quieres trabajar en esta sesión.
          </p>
        </div>

        {/* Role cards */}
        <div className={styles.cards}>
          {operationalRoles.map((role) => {
            const meta = ROLE_META[role];
            return (
              <button
                key={role}
                className={styles.card}
                onClick={() => handleSelect(role)}
                style={{ '--role-color': meta.color } as React.CSSProperties}
              >
                <span className={styles.cardIcon} style={{ color: meta.color, background: meta.color + '18' }}>
                  {meta.icon}
                </span>
                <span className={styles.cardLabel}>{meta.label}</span>
                <span className={styles.cardDesc}>{meta.description}</span>
                <span className={styles.cardCta}>Ingresar como {meta.label} →</span>
              </button>
            );
          })}
        </div>

        {/* Footer */}
        <p className={styles.footer}>
          ¿No eres tú?{' '}
          <button className={styles.footerLink} onClick={() => navigate('/login', { replace: true })}>
            Cerrar sesión
          </button>
        </p>
      </div>
    </div>
  );
};

export default RoleSelectPage;
