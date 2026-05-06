import { useState } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import Button from '../components/common/Button';
import styles from './PerfilPage.module.css';

const PerfilPage = () => {
  const { user, login } = useAuth();

  const [name, setName]   = useState(user?.name ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [currentPwd, setCurrentPwd] = useState('');
  const [newPwd, setNewPwd]         = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');

  const [profileSaved, setProfileSaved] = useState(false);
  const [pwdSaved, setPwdSaved]         = useState(false);
  const [pwdError, setPwdError]         = useState('');

  const handleSaveProfile = () => {
    if (!user) return;
    login({ ...user, name, email });
    setProfileSaved(true);
    setTimeout(() => setProfileSaved(false), 2500);
  };

  const handleSavePwd = () => {
    setPwdError('');
    if (!user) return;
    if (currentPwd !== user.password) {
      setPwdError('La contraseña actual no es correcta.');
      return;
    }
    if (newPwd.length < 6) {
      setPwdError('La nueva contraseña debe tener al menos 6 caracteres.');
      return;
    }
    if (newPwd !== confirmPwd) {
      setPwdError('Las contraseñas no coinciden.');
      return;
    }
    login({ ...user, password: newPwd });
    setCurrentPwd('');
    setNewPwd('');
    setConfirmPwd('');
    setPwdSaved(true);
    setTimeout(() => setPwdSaved(false), 2500);
  };

  if (!user) return null;

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <h2 className={styles.title}>Mi Perfil</h2>
        <p className={styles.subtitle}>Actualiza tu información y contraseña</p>
      </div>

      <div className={styles.sections}>
        {/* Datos personales */}
        <section className={styles.card}>
          <div className={styles.cardHeader}>
            <h3 className={styles.cardTitle}>Información de cuenta</h3>
            {profileSaved && <span className={styles.savedMsg}>✓ Guardado</span>}
          </div>

          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.label} htmlFor="pf-name">Nombre completo</label>
              <input
                id="pf-name"
                type="text"
                className={styles.input}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className={styles.fieldGroup}>
              <label className={styles.label} htmlFor="pf-email">Correo electrónico</label>
              <input
                id="pf-email"
                type="email"
                className={styles.input}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className={styles.fieldGroup}>
              <label className={styles.label}>Rol</label>
              <div className={styles.roleDisplay}>
                <span className={[styles.roleBadge, styles[`role_${user.role.toLowerCase()}`]].join(' ')}>
                  {ROLE_LABEL[user.role]}
                </span>
                {user.area && <span className={styles.areaBadge}>{user.area}</span>}
              </div>
            </div>
          </div>

          <div className={styles.cardFooter}>
            <Button
              variant="primary"
              size="sm"
              onClick={handleSaveProfile}
              disabled={!name.trim() || !email.trim()}
            >
              Guardar cambios
            </Button>
          </div>
        </section>

        {/* Cambio de contraseña */}
        <section className={styles.card}>
          <div className={styles.cardHeader}>
            <h3 className={styles.cardTitle}>Cambiar contraseña</h3>
            {pwdSaved && <span className={styles.savedMsg}>✓ Contraseña actualizada</span>}
          </div>

          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.label} htmlFor="pf-current">Contraseña actual</label>
              <input
                id="pf-current"
                type="password"
                className={styles.input}
                value={currentPwd}
                onChange={(e) => setCurrentPwd(e.target.value)}
                placeholder="••••••••"
              />
            </div>
            <div className={styles.fieldGroup}>
              <label className={styles.label} htmlFor="pf-new">Nueva contraseña</label>
              <input
                id="pf-new"
                type="password"
                className={styles.input}
                value={newPwd}
                onChange={(e) => setNewPwd(e.target.value)}
                placeholder="Mínimo 6 caracteres"
              />
            </div>
            <div className={styles.fieldGroup}>
              <label className={styles.label} htmlFor="pf-confirm">Confirmar contraseña</label>
              <input
                id="pf-confirm"
                type="password"
                className={styles.input}
                value={confirmPwd}
                onChange={(e) => setConfirmPwd(e.target.value)}
                placeholder="Repite la nueva contraseña"
              />
            </div>
            {pwdError && <p className={styles.errorMsg}>{pwdError}</p>}
          </div>

          <div className={styles.cardFooter}>
            <Button
              variant="primary"
              size="sm"
              onClick={handleSavePwd}
              disabled={!currentPwd || !newPwd || !confirmPwd}
            >
              Actualizar contraseña
            </Button>
          </div>
        </section>
      </div>
    </div>
  );
};

const ROLE_LABEL: Record<string, string> = {
  ADMIN:   'Administrador',
  DPO:     'DPO',
  USER:    'Usuario',
  TITULAR: 'Titular de datos',
};

export default PerfilPage;
