import { useState } from 'react';
import { MOCK_USERS, AREAS } from '../features/auth/mockUsers';
import type { AppUser } from '../features/auth/mockUsers';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import styles from './UsuariosPage.module.css';

const initials = (name: string) =>
  name.split(' ').slice(0, 2).map((w) => w[0]).join('').toUpperCase();

const ROLE_LABEL: Record<AppUser['role'], string> = {
  ADMIN: 'Administrador',
  DPO: 'DPO',
  USER: 'Responsable de área',
};

const UsuariosPage = () => {
  const [users, setUsers] = useState<AppUser[]>(MOCK_USERS);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', area: AREAS[0] });
  const [saved, setSaved] = useState(false);

  const handleCreate = () => {
    if (!form.name.trim() || !form.email.trim()) return;
    const newUser: AppUser = {
      id: `u${Date.now()}`,
      name: form.name.trim(),
      email: form.email.trim(),
      role: 'USER',
      area: form.area,
    };
    setUsers((prev) => [...prev, newUser]);
    setShowModal(false);
    setForm({ name: '', email: '', area: AREAS[0] });
    setSaved(true);
    setTimeout(() => setSaved(false), 2500);
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Usuarios</h2>
          <p className={styles.subtitle}>Responsables de área registrados en el sistema</p>
        </div>
        <div className={styles.headerActions}>
          {saved && <span className={styles.savedMsg}>✓ Usuario creado</span>}
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
                      {ROLE_LABEL[user.role]}
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
      <Modal open={showModal} onClose={() => setShowModal(false)} variant="center">
        <div className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>Agregar responsable de área</h3>
            <button className={styles.closeBtn} onClick={() => setShowModal(false)}>✕</button>
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
            <Button variant="ghost" onClick={() => setShowModal(false)}>Cancelar</Button>
            <Button
              variant="primary"
              onClick={handleCreate}
              disabled={!form.name.trim() || !form.email.trim()}
            >
              Crear usuario
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default UsuariosPage;
