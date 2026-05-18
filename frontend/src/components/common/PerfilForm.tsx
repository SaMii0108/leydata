import { useState } from 'react';
import Button from './Button';
import styles from './PerfilForm.module.css';

interface PerfilFormProps {
  initialName: string;
  initialEmail: string;
  currentPassword: string;
  role: string;
  area: string | null;
  onSaveProfile: (name: string, email: string) => void;
  onSavePassword: (current: string, next: string) => string | null;
}

const ROLE_LABEL: Record<string, string> = {
  ADMIN:   'Administrador',
  DPO:     'DPO',
  USER:    'Usuario',
  TITULAR: 'Titular de datos',
};

const PerfilForm = ({
  initialName,
  initialEmail,
  currentPassword,
  role,
  area,
  onSaveProfile,
  onSavePassword,
}: PerfilFormProps) => {
  const [name, setName]   = useState(initialName);
  const [email, setEmail] = useState(initialEmail);

  const [currentPwd, setCurrentPwd] = useState('');
  const [newPwd, setNewPwd]         = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');

  const [profileSaved, setProfileSaved] = useState(false);
  const [pwdSaved, setPwdSaved]         = useState(false);
  const [pwdError, setPwdError]         = useState('');

  const handleSaveProfile = () => {
    onSaveProfile(name, email);
    setProfileSaved(true);
    setTimeout(() => setProfileSaved(false), 2500);
  };

  const handleSavePwd = () => {
    setPwdError('');
    if (currentPwd !== currentPassword) {
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
    const result = onSavePassword(currentPwd, newPwd);
    if (result) { setPwdError(result); return; }
    setCurrentPwd('');
    setNewPwd('');
    setConfirmPwd('');
    setPwdSaved(true);
    setTimeout(() => setPwdSaved(false), 2500);
  };

  return (
    <div className={styles.sections}>
      {/* Información de cuenta */}
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
              <span className={[styles.roleBadge, styles[`role_${role.toLowerCase()}`]].join(' ')}>
                {ROLE_LABEL[role] ?? role}
              </span>
              {area && <span className={styles.areaBadge}>{area}</span>}
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

      {/* Cambiar contraseña */}
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
  );
};

export default PerfilForm;
