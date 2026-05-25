import { useAuth } from '../features/auth/useAuth';
import { updateMockUser } from '../features/auth/mockUsers';
import PerfilForm from '../components/common/PerfilForm';
import styles from './PerfilPage.module.css';

const PerfilPage = () => {
  const { user, login } = useAuth();
  if (!user) return null;

  const handleSaveProfile = (name: string) => {
    updateMockUser(user.id, { name });
    login({ ...user, name });
  };

  const handleSavePassword = (_current: string, next: string): string | null => {
    updateMockUser(user.id, { password: next });
    login({ ...user, password: next });
    return null;
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <h2 className={styles.title}>Mi Perfil</h2>
        <p className={styles.subtitle}>Actualiza tu información y contraseña</p>
      </div>

      <PerfilForm
        initialName={user.name}
        initialEmail={user.email}
        currentPassword={user.password}
        role={user.role}
        domains={user.domains}
        onSaveProfile={handleSaveProfile}
        onSavePassword={handleSavePassword}
      />
    </div>
  );
};

export default PerfilPage;
