import { useAuth } from '../features/auth/AuthContext';
import { updateMockUser } from '../features/auth/mockUsers';
import { updateUser } from '../api/usersApi';
import PerfilForm from '../components/common/PerfilForm';
import styles from './PerfilPage.module.css';

const PerfilPage = () => {
  const { user, login, accessToken } = useAuth();
  if (!user) return null;

  const handleSaveProfile = (name: string, email: string) => {
    if (accessToken) {
      // Keycloak user — persiste al backend; actualiza estado local inmediatamente
      updateUser(user.id, { name, email }, accessToken).catch(() => {
        // Error silencioso: el estado local ya se actualizó
      });
    } else {
      updateMockUser(user.id, { name, email });
    }
    login({ ...user, name, email });
  };

  const handleSavePassword = (_current: string, next: string): string | null => {
    // Solo aplica para titulares (auth mock). Usuarios Keycloak gestionan su contraseña externamente.
    if (user.role === 'TITULAR') {
      updateMockUser(user.id, { password: next });
      login({ ...user, password: next });
    }
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
        area={user.area}
        onSaveProfile={handleSaveProfile}
        onSavePassword={handleSavePassword}
      />
    </div>
  );
};

export default PerfilPage;
