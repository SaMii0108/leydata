import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth/useAuth';
import type { Role } from '../../features/auth/mockUsers';
import { initials } from '../../utils/formatters';
import RoleSwitcher from '../common/RoleSwitcher';
import styles from './Sidebar.module.css';

// ── Types ────────────────────────────────────────────────────────
interface NavItem {
  to: string;
  label: string;
  icon: ReactElement;
  roles: Role[];
}

interface NavGroupDef {
  id: string;
  label: string;
  icon: ReactElement;
  childRoutes: string[];
  children: NavItem[];
}

type NavEntry =
  | ({ kind: 'item' } & NavItem)
  | ({ kind: 'group' } & NavGroupDef);

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

// ── Icons ────────────────────────────────────────────────────────
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
const IconLayers = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polygon points="12 2 2 7 12 12 22 7 12 2"/>
    <polyline points="2 17 12 22 22 17"/>
    <polyline points="2 12 12 17 22 12"/>
  </svg>
);
const IconChevron = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="6 9 12 15 18 9"/>
  </svg>
);

// ── Navigation entries ───────────────────────────────────────────
const navEntries: NavEntry[] = [
  { kind: 'item', to: '/',             label: 'Métricas',           icon: <IconGrid />,      roles: ['ADMIN', 'DPO', 'JEFE_DOMINIO'] },
  { kind: 'item', to: '/dominios',     label: 'Dominios',           icon: <IconDomain />,    roles: ['ADMIN'] },
  {
    kind: 'group',
    id: 'consentimientos-plantillas',
    label: 'Plantillas y consentimientos',
    icon: <IconLayers />,
    childRoutes: ['/consentimientos', '/plantillas'],
    children: [
      { to: '/consentimientos', label: 'Consentimientos', icon: <IconList />,    roles: ['DPO', 'JEFE_DOMINIO'] },
      { to: '/plantillas',      label: 'Plantillas',      icon: <IconPalette />, roles: ['DPO'] },
    ],
  },
  { kind: 'item', to: '/solicitudes',  label: 'Solicitudes',        icon: <IconClipboard />, roles: ['DPO', 'JEFE_DOMINIO'] },
  { kind: 'item', to: '/usuarios',     label: 'Usuarios',           icon: <IconUsers />,     roles: ['ADMIN'] },
  { kind: 'item', to: '/auditoria',    label: 'Auditoría',          icon: <IconAudit />,     roles: ['DPO'] },
  { kind: 'item', to: '/cumplimiento', label: 'Cumplimiento Legal', icon: <IconShield />,    roles: ['DPO'] },
  { kind: 'item', to: '/perfil',       label: 'Mi Perfil',          icon: <IconUser />,      roles: ['ADMIN', 'DPO', 'JEFE_DOMINIO'] },
];

// ── Role colors ──────────────────────────────────────────────────
const ROLE_COLOR: Record<Role, string> = {
  ADMIN:        '#4361ee',
  DPO:          '#7c3aed',
  JEFE_DOMINIO: '#059669',
  TITULAR:      '#0891b2',
};

// ── Component ────────────────────────────────────────────────────
const Sidebar = ({ isOpen, onClose }: SidebarProps) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [confirmLogout, setConfirmLogout] = useState(false);

  // Initialize expanded groups based on current path
  const [openGroups, setOpenGroups] = useState<Set<string>>(() => {
    const initial = new Set<string>();
    navEntries.forEach((entry) => {
      if (
        entry.kind === 'group' &&
        entry.childRoutes.some((r) => location.pathname.startsWith(r))
      ) {
        initial.add(entry.id);
      }
    });
    return initial;
  });

  // Close mobile sidebar and reset logout confirmation on navigation
  useEffect(() => {
    Promise.resolve().then(() => {
      onClose();
      setConfirmLogout(false);
    });
  }, [location.pathname, onClose]);

  // Auto-expand groups when navigating into one of their child routes
  useEffect(() => {
    navEntries.forEach((entry) => {
      if (
        entry.kind === 'group' &&
        entry.childRoutes.some((r) => location.pathname.startsWith(r))
      ) {
        setOpenGroups((prev) => {
          if (prev.has(entry.id)) return prev;
          return new Set([...prev, entry.id]);
        });
      }
    });
  }, [location.pathname]);

  const toggleGroup = (id: string) =>
    setOpenGroups((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

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
          {navEntries.map((entry) => {
            // ── Flat item ────────────────────────────────────────
            if (entry.kind === 'item') {
              if (!user || !entry.roles.includes(user.role)) return null;
              return (
                <NavLink
                  key={entry.to}
                  to={entry.to}
                  end={entry.to === '/'}
                  className={({ isActive }) =>
                    [styles.navItem, isActive ? styles.active : ''].join(' ')
                  }
                >
                  <span className={styles.icon}>{entry.icon}</span>
                  {entry.label}
                </NavLink>
              );
            }

            // ── Group ────────────────────────────────────────────
            const visibleChildren = entry.children.filter(
              (c) => user && c.roles.includes(user.role),
            );
            if (visibleChildren.length === 0) return null;

            const isGroupActive = entry.childRoutes.some((r) =>
              location.pathname.startsWith(r),
            );
            const isExpanded = openGroups.has(entry.id);

            return (
              <div key={entry.id} className={styles.navGroup}>
                <button
                  className={[
                    styles.navGroupBtn,
                    isGroupActive ? styles.navGroupBtnActive : '',
                  ].join(' ')}
                  onClick={() => toggleGroup(entry.id)}
                  aria-expanded={isExpanded}
                >
                  <span className={styles.icon}>{entry.icon}</span>
                  <span className={styles.navGroupLabel}>{entry.label}</span>
                  <span
                    className={[
                      styles.navGroupChevron,
                      isExpanded ? styles.navGroupChevronOpen : '',
                    ].join(' ')}
                  >
                    <IconChevron />
                  </span>
                </button>

                {isExpanded && (
                  <div className={styles.navGroupChildren}>
                    {visibleChildren.map((child) => (
                      <NavLink
                        key={child.to}
                        to={child.to}
                        className={({ isActive: childActive }) =>
                          [
                            styles.navSubItem,
                            childActive ? styles.navSubItemActive : '',
                          ].join(' ')
                        }
                      >
                        <span className={styles.icon}>{child.icon}</span>
                        {child.label}
                      </NavLink>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </nav>

        {/* User panel */}
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
                <RoleSwitcher variant="sidebar" />
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
