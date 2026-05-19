import { useState } from 'react';
import { MOCK_USERS, AREAS, addMockUser } from '../features/auth/mockUsers';
import type { AppUser } from '../features/auth/mockUsers';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import { ROLE_LABEL } from '../constants/labels';
import { initials } from '../utils/formatters';
import styles from './UsuariosPage.module.css';

const USUARIO_LABEL: Record<string, string> = {
  ...ROLE_LABEL,
  USER: 'Responsable de área',
};

const generatePassword = () =>
  'LEY-' + Math.random().toString(36).slice(2, 8).toUpperCase();

const UsuariosPage = () => {
  const internalUsers = MOCK_USERS.filter((u) => u.role !== 'TITULAR');
  const [users, setUsers] = useState<AppUser[]>(internalUsers);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', area: AREAS[0] });
  const [provisionalPassword, setProvisionalPassword] = useState<string | null>(null);

  const handleCreate = () => {
    if (!form.name.trim() || !form.email.trim()) return;
    const password = generatePassword();
    const newUser: AppUser = {
      id: `u${Date.now()}`,
      name: form.name.trim(),
      email: form.email.trim(),
      password,
      role: 'USER',
      area: form.area,
    };
    addMockUser(newUser);
    setUsers((prev) => [...prev, newUser]);
    setForm({ name: '', email: '', area: AREAS[0] });
    setProvisionalPassword(password);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setProvisionalPassword(null);
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Usuarios</h2>
          <p className={styles.subtitle}>Responsables de área registrados en el sistema</p>
        </div>
        <div className={styles.headerActions}>
          <Button variant="primary" size="sm" onClick={() => setShowModal(true)}>
            + Agregar responsable
          </Button>
        </div>
      </div>

      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Correo</th>
                <th>Rol</th>
                <th>Área</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>
                    <div className={styles.userCell}>
                      <span className={[styles.avatar, styles[`avatar_${user.role.toLowerCase()}`]].join(' ')}>
                        {initials(user.name)}
                      </span>
                      <span className={styles.userName}>{user.name}</span>
                    </div>
                  </td>
                  <td className={styles.cellMono}>{user.email}</td>
                  <td>
                    <span className={[styles.roleBadge, styles[`role_${user.role.toLowerCase()}`]].join(' ')}>
                      {USUARIO_LABEL[user.role]}
                    </span>
                  </td>
                  <td className={styles.cellArea}>
                    {user.area ?? <span className={styles.noArea}>—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Create user modal */}
      <Modal open={showModal} onClose={handleCloseModal} variant="center">
        <div className={styles.modal}>
          {provisionalPassword ? (
            /* ── Success view ── */
            <>
              <div className={styles.modalHeader}>
                <h3 className={styles.modalTitle}>Usuario creado</h3>
                <button className={styles.closeBtn} onClick={handleCloseModal}>✕</button>
              </div>
              <div className={styles.successBody}>
                <div className={styles.successIcon}>
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="9 12 11 14 15 10" />
                  </svg>
                </div>
                <p className={styles.successMsg}>
                  La cuenta ha sido creada. Comparte la contraseña provisoria con el usuario — deberá cambiarla en su primer acceso.
                </p>
                <div className={styles.passwordBox}>
                  <span className={styles.passwordLabel}>Contraseña provisoria</span>
                  <span className={styles.passwordValue}>{provisionalPassword}</span>
                </div>
                <p className={styles.passwordHint}>
                  En producción, esto se enviaría automáticamente al correo del usuario.
                </p>
              </div>
              <div className={styles.modalFooter}>
                <Button variant="primary" onClick={handleCloseModal}>Entendido</Button>
              </div>
            </>
          ) : (
            /* ── Form view ── */
            <>
              <div className={styles.modalHeader}>
                <h3 className={styles.modalTitle}>Agregar responsable de área</h3>
                <button className={styles.closeBtn} onClick={handleCloseModal}>✕</button>
              </div>
              <p className={styles.modalHint}>
                Se creará una cuenta con rol <strong>Usuario</strong> asignada al área seleccionada.
              </p>

              <div className={styles.fields}>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="u-name">Nombre completo</label>
                  <input
                    id="u-name"
                    type="text"
                    className={styles.input}
                    placeholder="Ej: Juan Pérez González"
                    value={form.name}
                    onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  />
                </div>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="u-email">Correo electrónico</label>
                  <input
                    id="u-email"
                    type="email"
                    className={styles.input}
                    placeholder="correo@empresa.cl"
                    value={form.email}
                    onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                  />
                </div>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="u-area">Área asignada</label>
                  <select
                    id="u-area"
                    className={styles.select}
                    value={form.area}
                    onChange={(e) => setForm((f) => ({ ...f, area: e.target.value }))}
                  >
                    {AREAS.map((a) => (
                      <option key={a} value={a}>{a}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className={styles.modalFooter}>
                <Button variant="ghost" onClick={handleCloseModal}>Cancelar</Button>
                <Button
                  variant="primary"
                  onClick={handleCreate}
                  disabled={!form.name.trim() || !form.email.trim()}
                >
                  Crear usuario
                </Button>
              </div>
            </>
          )}
        </div>
      </Modal>
    </div>
  );
};

export default UsuariosPage;
