import { useState } from 'react';
import Button from './Button';
import { ROLE_LABEL } from '../../constants/labels';
import styles from './PerfilForm.module.css';

interface PerfilFormProps {
  initialName: string;
  email: string;
  role: string;
  area: string | null;
  onSaveProfile: (name: string) => void;
  onSavePassword: (newPassword: string) => Promise<string | null>;
}

const PerfilForm = ({
  initialName,
  email,
  role,
  area,
  onSaveProfile,
  onSavePassword,
}: PerfilFormProps) => {
  const [name, setName] = useState(initialName);

  const [newPwd, setNewPwd]         = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');

  const [profileSaved, setProfileSaved] = useState(false);
  const [pwdSaved, setPwdSaved]         = useState(false);
  const [pwdError, setPwdError]         = useState('');
  const [pwdLoading, setPwdLoading]     = useState(false);

  const handleSaveProfile = () => {
    onSaveProfile(name);
    setProfileSaved(true);
    setTimeout(() => setProfileSaved(false), 2500);
  };

  const handleSavePwd = async () => {
    setPwdError('');
    if (newPwd.length < 8) {
      setPwdError('La nueva contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (newPwd !== confirmPwd) {
      setPwdError('Las contraseñas no coinciden.');
      return;
    }
    setPwdLoading(true);
    const result = await onSavePassword(newPwd);
    setPwdLoading(false);
    if (result) { setPwdError(result); return; }
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
              className={[styles.input, styles.inputReadonly].join(' ')}
              value={email}
              readOnly
              tabIndex={-1}
            />
            <span className={styles.fieldHint}>El correo no puede modificarse desde aquí.</span>
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
            disabled={!name.trim()}
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
            <label className={styles.label} htmlFor="pf-new">Nueva contraseña</label>
            <input
              id="pf-new"
              type="password"
              className={styles.input}
              value={newPwd}
              onChange={(e) => setNewPwd(e.target.value)}
              placeholder="Mínimo 8 caracteres"
              disabled={pwdLoading}
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
              disabled={pwdLoading}
            />
          </div>
          {pwdError && <p className={styles.errorMsg}>{pwdError}</p>}
        </div>

        <div className={styles.cardFooter}>
          <Button
            variant="primary"
            size="sm"
            onClick={handleSavePwd}
            disabled={!newPwd || !confirmPwd || pwdLoading}
          >
            {pwdLoading ? 'Actualizando…' : 'Actualizar contraseña'}
          </Button>
        </div>
      </section>
    </div>
  );
};

export default PerfilForm;
