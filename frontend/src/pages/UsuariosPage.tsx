import { useState, useEffect } from 'react';
import { AREAS } from '../features/auth/mockUsers';
import { useAuth } from '../features/auth/AuthContext';
import { getUsers, getUser, createUser, ApiError } from '../api/usersApi';
import type { UserSummaryDto } from '../api/usersApi';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import { ROLE_LABEL } from '../constants/labels';
import { initials } from '../utils/formatters';
import styles from './UsuariosPage.module.css';

const USUARIO_LABEL: Record<string, string> = {
  ...ROLE_LABEL,
  JEFE_DOMINIO: 'Responsable de área',
};

const generatePassword = () =>
  'LEY-' + Math.random().toString(36).slice(2, 8).toUpperCase();

const primaryRole = (u: UserSummaryDto): string => u.roles[0] ?? '';
const primaryArea = (u: UserSummaryDto): string | null => u.domains[0] ?? null;

const UsuariosPage = () => {
  const { accessToken } = useAuth();
  const [users, setUsers] = useState<UserSummaryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState<{ name: string; email: string; area: string }>({ name: '', email: '', area: AREAS[0] });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [provisionalPassword, setProvisionalPassword] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFetchError(null);
    getUsers(accessToken)
      .then((data) => {
        if (!cancelled) setUsers(data.filter((u) => !u.roles.every((r) => r === 'TITULAR')));
      })
      .catch((err) => {
        if (!cancelled)
          setFetchError(err instanceof ApiError ? err.message : 'No se pudieron cargar los usuarios');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [accessToken]);

  const handleCreate = async () => {
    if (!form.name.trim() || !form.email.trim()) return;
    const password = generatePassword();
    setCreating(true);
    setCreateError(null);
    try {
      const userId = await createUser(
        { name: form.name.trim(), email: form.email.trim(), password, roleCode: 'JEFE_DOMINIO' },
        accessToken,
      );
      setForm({ name: '', email: '', area: AREAS[0] });
      setProvisionalPassword(password);
      getUser(userId, accessToken)
        .then((created) => setUsers((prev) => [...prev, created]))
        .catch(() => {});
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Error al crear el usuario');
    } finally {
      setCreating(false);
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setProvisionalPassword(null);
    setCreateError(null);
    setCreating(false);
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

      {fetchError && (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
          {fetchError}
        </p>
      )}

      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {loading ? (
            <p style={{ padding: '24px 16px', margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
              Cargando usuarios…
            </p>
          ) : (
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
                        <span className={[styles.avatar, styles[`avatar_${primaryRole(user).toLowerCase()}`]].join(' ')}>
                          {initials(user.name)}
                        </span>
                        <span className={styles.userName}>{user.name}</span>
                      </div>
                    </td>
                    <td className={styles.cellMono}>{user.email}</td>
                    <td>
                      <span className={[styles.roleBadge, styles[`role_${primaryRole(user).toLowerCase()}`]].join(' ')}>
                        {USUARIO_LABEL[primaryRole(user)] ?? primaryRole(user)}
                      </span>
                    </td>
                    <td className={styles.cellArea}>
                      {primaryArea(user) ?? <span className={styles.noArea}>—</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <Modal open={showModal} onClose={handleCloseModal} variant="center">
        <div className={styles.modal}>
          {provisionalPassword ? (
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

              {createError && (
                <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
                  {createError}
                </p>
              )}

              <div className={styles.modalFooter}>
                <Button variant="ghost" onClick={handleCloseModal}>Cancelar</Button>
                <Button
                  variant="primary"
                  onClick={handleCreate}
                  disabled={!form.name.trim() || !form.email.trim() || creating}
                >
                  {creating ? 'Creando…' : 'Crear usuario'}
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
