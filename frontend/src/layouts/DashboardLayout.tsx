import { Outlet } from 'react-router-dom';
import Sidebar from '../components/layout/Sidebar';
import Header from '../components/layout/Header';
import styles from './DashboardLayout.module.css';

const DashboardLayout = () => (
  <div className={styles.shell}>
    <Sidebar />
    <div className={styles.main}>
      <Header />
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  </div>
);

export default DashboardLayout;
