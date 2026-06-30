import { useAuth } from '../features/auth/AuthContext';
import { updateMockUser } from '../features/auth/mockUsers';
import { changeOwnPassword, ApiError } from '../api/usersApi';
import PerfilForm from '../components/common/PerfilForm';
import styles from './PerfilPage.module.css';

const PerfilPage = () => {
  const { user, accessToken, login } = useAuth();
  if (!user) return null;

  const isKeycloakUser = user.role !== 'TITULAR';

  const handleSaveProfile = (name: string) => {
    if (!isKeycloakUser) {
      updateMockUser(user.id, { name, email: user.email });
    }
    login({ ...user, name });
  };

  const handleSavePassword = async (newPassword: string): Promise<string | null> => {
    if (isKeycloakUser) {
      try {
        await changeOwnPassword(user.id, newPassword, accessToken);
        return null;
      } catch (err) {
        return err instanceof ApiError ? err.message : 'Error al actualizar la contraseña';
      }
    } else {
      updateMockUser(user.id, { password: newPassword });
      login({ ...user, password: newPassword });
      return null;
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <h2 className={styles.title}>Mi Perfil</h2>
        <p className={styles.subtitle}>Actualiza tu información y contraseña</p>
      </div>

      <PerfilForm
        initialName={user.name}
        email={user.email}
        role={user.role}
        area={user.area}
        onSaveProfile={handleSaveProfile}
        onSavePassword={handleSavePassword}
      />
    </div>
  );
};

export default PerfilPage;
