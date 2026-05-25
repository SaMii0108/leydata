import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth/useAuth';
import type { Role } from '../../features/auth/mockUsers';
import { ROLE_LABEL } from '../../constants/labels';
import { initials } from '../../utils/formatters';
import styles from './Sidebar.module.css';

interface NavItem {
  to: string;
  label: string;
  icon: ReactElement;
  roles: Role[];
}

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

const IconGrid = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
    <rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>
  </svg>
);
const IconList = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>
  </svg>
);
const IconUsers = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
    <circle cx="9" cy="7" r="4"/>
    <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
  </svg>
);
const IconPalette = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="13.5" cy="6.5" r=".5"/><circle cx="17.5" cy="10.5" r=".5"/>
    <circle cx="8.5" cy="7.5" r=".5"/><circle cx="6.5" cy="12.5" r=".5"/>
    <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"/>
  </svg>
);
const IconAudit = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
    <polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
    <polyline points="10 9 9 9 8 9"/>
  </svg>
);
const IconShield = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
  </svg>
);
const IconUser = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
  </svg>
);

const IconDomain = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <ellipse cx="12" cy="5" rx="9" ry="3"/>
    <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/>
    <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>
  </svg>
);

const IconClipboard = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
    <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
    <line x1="9" y1="12" x2="15" y2="12"/>
    <line x1="9" y1="16" x2="13" y2="16"/>
  </svg>
);

const navItems: NavItem[] = [
  { to: '/',                label: 'Métricas Generales', icon: <IconGrid />,       roles: ['ADMIN', 'DPO'] },
  { to: '/dominios',        label: 'Dominios',           icon: <IconDomain />,     roles: ['ADMIN'] },
  { to: '/consentimientos', label: 'Consentimientos',    icon: <IconList />,       roles: ['DPO', 'JEFE_DOMINIO'] },
  { to: '/solicitudes',     label: 'Solicitudes',        icon: <IconClipboard />,  roles: ['DPO', 'JEFE_DOMINIO'] },
  { to: '/usuarios',        label: 'Usuarios',           icon: <IconUsers />,      roles: ['ADMIN'] },
  { to: '/auditoria',       label: 'Auditoría',          icon: <IconAudit />,      roles: ['ADMIN', 'DPO'] },
  { to: '/cumplimiento',    label: 'Cumplimiento Legal', icon: <IconShield />,     roles: ['ADMIN', 'DPO'] },
  { to: '/plantillas',      label: 'Plantillas',         icon: <IconPalette />,    roles: ['DPO'] },
  { to: '/perfil',          label: 'Mi Perfil',          icon: <IconUser />,       roles: ['ADMIN', 'DPO', 'JEFE_DOMINIO'] },
];

const ROLE_COLOR: Record<Role, string> = {
  ADMIN:        '#4361ee',
  DPO:          '#7c3aed',
  JEFE_DOMINIO: '#059669',
  TITULAR:      '#0891b2',
};

const Sidebar = ({ isOpen, onClose }: SidebarProps) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [confirmLogout, setConfirmLogout] = useState(false);

  useEffect(() => {
    // Cierra el menú móvil y resetea confirmación al navegar
    Promise.resolve().then(() => {
      onClose();
      setConfirmLogout(false);
    });
  }, [location.pathname, onClose]);

  const visible = navItems.filter((item) => user && item.roles.includes(user.role));

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <>
      {isOpen && <div className={styles.overlay} onClick={onClose} />}
      <aside className={[styles.sidebar, isOpen ? styles.sidebarOpen : ''].join(' ')}>
      {/* Brand */}
      <div className={styles.brand}>
        <span className={styles.brandName}>Ley Data</span>
        <span className={styles.brandSub}>Gestión de Privacidad</span>
      </div>

      {/* Nav */}
      <nav className={styles.nav}>
        {visible.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              [styles.navItem, isActive ? styles.active : ''].join(' ')
            }
          >
            <span className={styles.icon}>{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* User info */}
      {user && (
        <div className={styles.userPanel}>
          <div className={styles.userRow}>
            <span
              className={styles.userAvatar}
              style={{ background: ROLE_COLOR[user.role] + '22', color: ROLE_COLOR[user.role] }}
            >
              {initials(user.name)}
            </span>
            <div className={styles.userInfo}>
              <span className={styles.userName}>{user.name}</span>
              <span className={styles.userRole} style={{ color: ROLE_COLOR[user.role] }}>
                {ROLE_LABEL[user.role]}
                {user.domains.length > 0 && ` · ${user.domains[0]}`}
              </span>
            </div>
          </div>
          {!confirmLogout ? (
            <button className={styles.logoutBtn} onClick={() => setConfirmLogout(true)}>
              Cerrar sesión
            </button>
          ) : (
            <div className={styles.logoutConfirm}>
              <span className={styles.logoutConfirmText}>¿Cerrar sesión?</span>
              <div className={styles.logoutConfirmBtns}>
                <button className={styles.logoutConfirmYes} onClick={handleLogout}>Sí</button>
                <button className={styles.logoutConfirmNo} onClick={() => setConfirmLogout(false)}>No</button>
              </div>
            </div>
          )}
        </div>
      )}
    </aside>
    </>
  );
};

export default Sidebar;
