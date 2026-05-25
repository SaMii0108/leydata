import { useState, useCallback, useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from '../components/layout/Sidebar';
import Header from '../components/layout/Header';
import styles from './DashboardLayout.module.css';

const DESKTOP_MQ = '(min-width: 769px)';

const DashboardLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const closeSidebar  = useCallback(() => setSidebarOpen(false), []);
  const toggleSidebar = useCallback(() => setSidebarOpen((prev) => !prev), []);

  // Cierra el sidebar automáticamente al pasar a layout desktop
  useEffect(() => {
    const mql = window.matchMedia(DESKTOP_MQ);
    const handleChange = (e: MediaQueryListEvent) => {
      if (e.matches) setSidebarOpen(false);
    };
    mql.addEventListener('change', handleChange);
    return () => mql.removeEventListener('change', handleChange);
  }, []);

  return (
    <div className={styles.shell}>
      <Sidebar isOpen={sidebarOpen} onClose={closeSidebar} />
      <div className={styles.main}>
        <Header onMenuToggle={toggleSidebar} />
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
