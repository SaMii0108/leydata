import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import type { Role } from '../features/auth/mockUsers';
import styles from './RoleSelectPage.module.css';

const ROLE_META: Record<string, { label: string; description: string; color: string }> = {
  ADMIN: {
    label:       'Administrador',
    description: 'Gestión de usuarios, métricas y configuración general del sistema.',
    color:       '#4361ee',
  },
  DPO: {
    label:       'DPO',
    description: 'Supervisión de consentimientos, auditoría y cumplimiento Ley 21.719.',
    color:       '#7c3aed',
  },
  JEFE_DOMINIO: {
    label:       'Jefe de dominio',
    description: 'Gestión de consentimientos del área asignada.',
    color:       '#059669',
  },
};

const destinationFor = (role: Role) =>
  role === 'JEFE_DOMINIO' ? '/consentimientos' : '/';

const RoleSelectPage = () => {
  const { pendingUser, selectRole } = useAuth();
  const navigate = useNavigate();

  // Solo en montaje: si no hay usuario pendiente (navegación directa a la URL),
  // redirigir al login. No debe reaccionar al cambio de pendingUser → null que
  // ocurre normalmente al confirmar el rol en handleSelect.
  useEffect(() => {
    if (!pendingUser) navigate('/login', { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!pendingUser) return null;

  const operationalRoles = (pendingUser.roles ?? [pendingUser.role]).filter(
    (r) => r !== 'TITULAR',
  ) as Role[];

  const handleSelect = (role: Role) => {
    selectRole(role);
    navigate(destinationFor(role), { replace: true });
  };

  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.brand}>Ley Data</span>
          <h1 className={styles.title}>¿Con qué rol deseas ingresar?</h1>
          <p className={styles.subtitle}>
            Hola, <strong>{pendingUser.name}</strong>. Tu cuenta tiene múltiples roles
            asignados. Elige el perfil con el que quieres trabajar en esta sesión.
          </p>
        </div>

        <div className={styles.cards}>
          {operationalRoles.map((role) => {
            const meta = ROLE_META[role] ?? { label: role, description: '', color: '#4361ee' };
            return (
              <button
                key={role}
                className={styles.card}
                onClick={() => handleSelect(role)}
              >
                <span
                  className={styles.cardIcon}
                  style={{ background: meta.color + '18', color: meta.color }}
                >
                  {meta.label.slice(0, 2).toUpperCase()}
                </span>
                <span className={styles.cardLabel}>{meta.label}</span>
                <span className={styles.cardDesc}>{meta.description}</span>
                <span className={styles.cardCta} style={{ color: meta.color }}>
                  Ingresar como {meta.label} →
                </span>
              </button>
            );
          })}
        </div>

        <button
          className={styles.backLink}
          onClick={() => navigate('/login', { replace: true })}
        >
          ← Volver al inicio de sesión
        </button>
      </div>
    </div>
  );
};

export default RoleSelectPage;
