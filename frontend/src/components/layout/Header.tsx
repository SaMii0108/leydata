import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { ROLE_LABEL } from '../../constants/labels';
import { initials, formatRelativeTime } from '../../utils/formatters';
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
} from '../../api/notificationsApi';
import type { NotificationDto } from '../../api/notificationsApi';
import styles from './Header.module.css';

interface HeaderProps {
  onMenuToggle: () => void;
}

const IconMenu = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="3" y1="6"  x2="21" y2="6"/>
    <line x1="3" y1="12" x2="21" y2="12"/>
    <line x1="3" y1="18" x2="21" y2="18"/>
  </svg>
);

const IconUser = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
  </svg>
);

const IconLogout = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
    <polyline points="16 17 21 12 16 7"/>
    <line x1="21" y1="12" x2="9" y2="12"/>
  </svg>
);

const Header = ({ onMenuToggle }: HeaderProps) => {
  const { user, logout, accessToken } = useAuth();
  const navigate = useNavigate();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [confirmLogout, setConfirmLogout] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const [notifOpen, setNotifOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationDto[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifLoading, setNotifLoading] = useState(false);
  const notifWrapRef = useRef<HTMLDivElement>(null);

  const userInitials = user ? initials(user.name) : '?';

  useEffect(() => {
    if (!accessToken) return;
    getUnreadCount(accessToken).then(setUnreadCount).catch(() => {});
  }, [accessToken]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
        setConfirmLogout(false);
      }
      if (notifWrapRef.current && !notifWrapRef.current.contains(e.target as Node)) {
        setNotifOpen(false);
      }
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setDropdownOpen(false);
        setConfirmLogout(false);
        setNotifOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const handleProfile = () => {
    setDropdownOpen(false);
    navigate('/perfil');
  };

  const handleNotifToggle = async () => {
    const next = !notifOpen;
    setNotifOpen(next);
    if (!next) return;
    setNotifLoading(true);
    try {
      const [items, count] = await Promise.all([
        getNotifications(accessToken),
        getUnreadCount(accessToken),
      ]);
      setNotifications(items);
      setUnreadCount(count);
    } catch {
      // silently ignore fetch errors in the panel
    } finally {
      setNotifLoading(false);
    }
  };

  const handleMarkRead = async (id: string) => {
    const updated = await markAsRead(id, accessToken).catch(() => null);
    if (updated) {
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n)),
      );
      setUnreadCount((c) => Math.max(0, c - 1));
    }
  };

  const handleMarkAllRead = async () => {
    await markAllAsRead(accessToken).catch(() => {});
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnreadCount(0);
  };

  return (
    <header className={styles.header}>
      <button className={styles.menuBtn} onClick={onMenuToggle} aria-label="Abrir menú">
        <IconMenu />
      </button>

      <div className={styles.search}>
        <svg className={styles.searchIcon} width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input
          type="text"
          placeholder="Buscar por usuario o correo..."
          className={styles.searchInput}
        />
      </div>

      <div className={styles.right}>
        {/* Notificaciones */}
        <div className={styles.notifWrap} ref={notifWrapRef}>
          <button
            className={styles.notifBtn}
            aria-label="Notificaciones"
            aria-expanded={notifOpen}
            onClick={handleNotifToggle}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
            {unreadCount > 0 && <span className={styles.notifDot} />}
          </button>

          {notifOpen && (
            <div className={styles.notifPanel}>
              <div className={styles.notifPanelHead}>
                <span className={styles.notifPanelTitle}>Notificaciones</span>
                {unreadCount > 0 && (
                  <button className={styles.notifMarkAll} onClick={handleMarkAllRead}>
                    Marcar todas como leídas
                  </button>
                )}
              </div>

              {notifLoading ? (
                <p className={styles.notifEmpty}>Cargando…</p>
              ) : notifications.length === 0 ? (
                <p className={styles.notifEmpty}>No tienes notificaciones</p>
              ) : (
                <ul className={styles.notifList}>
                  {notifications.map((n) => (
                    <li
                      key={n.id}
                      className={[
                        styles.notifItem,
                        !n.read ? styles.notifItemUnread : '',
                      ].join(' ').trim()}
                      onClick={() => { if (!n.read) handleMarkRead(n.id); }}
                    >
                      <span className={styles.notifItemTitle}>{n.title}</span>
                      {n.message && (
                        <span className={styles.notifItemMsg}>{n.message}</span>
                      )}
                      <span className={styles.notifItemTime}>
                        {formatRelativeTime(n.createdAt)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        {/* Avatar + dropdown */}
        <div className={styles.avatarWrap} ref={dropdownRef}>
          <button
            className={styles.avatar}
            onClick={() => { setDropdownOpen((o) => !o); setConfirmLogout(false); }}
            aria-label="Menú de usuario"
            aria-expanded={dropdownOpen}
          >
            {userInitials}
          </button>

          {dropdownOpen && (
            <div className={styles.dropdown}>
              <div className={styles.dropdownHeader}>
                <span className={styles.dropdownName}>{user?.name}</span>
                <span className={styles.dropdownRole}>
                  {ROLE_LABEL[user?.role ?? ''] ?? user?.role}
                  {user?.area ? ` · ${user.area}` : ''}
                </span>
              </div>

              <div className={styles.dropdownDivider} />

              <button className={styles.dropdownItem} onClick={handleProfile}>
                <IconUser />
                Editar perfil
              </button>

              <div className={styles.dropdownDivider} />

              {!confirmLogout ? (
                <button
                  className={[styles.dropdownItem, styles.dropdownItemDanger].join(' ')}
                  onClick={() => setConfirmLogout(true)}
                >
                  <IconLogout />
                  Cerrar sesión
                </button>
              ) : (
                <div className={styles.dropdownConfirm}>
                  <span className={styles.dropdownConfirmText}>¿Cerrar sesión?</span>
                  <div className={styles.dropdownConfirmBtns}>
                    <button className={styles.confirmYes} onClick={handleLogout}>Sí</button>
                    <button className={styles.confirmNo} onClick={() => setConfirmLogout(false)}>No</button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default Header;
