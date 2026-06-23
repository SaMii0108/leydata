import { useState, useCallback } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from '../components/layout/Sidebar';
import Header from '../components/layout/Header';
import ErrorBoundary from '../components/common/ErrorBoundary';
import styles from './DashboardLayout.module.css';

const DashboardLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  const handleClose = useCallback(() => setSidebarOpen(false), []);
  const handleToggle = useCallback(() => setSidebarOpen((prev) => !prev), []);

  return (
    <div className={styles.shell}>
      <Sidebar isOpen={sidebarOpen} onClose={handleClose} />
      <div className={styles.main}>
        <Header onMenuToggle={handleToggle} />
        <main className={styles.content}>
          <ErrorBoundary
            key={location.pathname}
            title="No se pudo cargar esta sección"
            message="Ocurrió un error al mostrar esta página. Puedes intentar de nuevo o navegar a otra sección desde el menú."
          >
            <Outlet />
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
