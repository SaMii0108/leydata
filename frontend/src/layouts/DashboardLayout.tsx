import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from '../components/layout/Sidebar';
import Header from '../components/layout/Header';
import styles from './DashboardLayout.module.css';

const DashboardLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className={styles.shell}>
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className={styles.main}>
        <Header onMenuToggle={() => setSidebarOpen((prev) => !prev)} />
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
