import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import styles from './TitularLayout.module.css';

const TitularLayout = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/titular/login', { replace: true });
  };

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <span className={styles.brand}>Ley Data</span>

        <nav className={styles.nav}>
          <NavLink
            to="/titular/mis-consentimientos"
            className={({ isActive }) => [styles.navLink, isActive ? styles.active : ''].join(' ')}
          >
            Mis consentimientos
          </NavLink>
          <NavLink
            to="/titular/perfil"
            className={({ isActive }) => [styles.navLink, isActive ? styles.active : ''].join(' ')}
          >
            Mi perfil
          </NavLink>
        </nav>

        <div className={styles.right}>
          {user && <span className={styles.userName}>{user.name}</span>}
          <button className={styles.logoutBtn} onClick={handleLogout}>
            Cerrar sesión
          </button>
        </div>
      </header>

      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
};

export default TitularLayout;
