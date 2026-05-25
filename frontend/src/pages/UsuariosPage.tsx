import { useState } from 'react';
import { MOCK_USERS, addMockUser, updateMockUser } from '../features/auth/mockUsers';
import type { AppUser, Role } from '../features/auth/mockUsers';
import { MOCK_DOMAINS } from '../features/domains/mockDomains';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import { ROLE_LABEL } from '../constants/labels';
import { initials } from '../utils/formatters';
import styles from './UsuariosPage.module.css';

/* ─── Labels ────────────────────────────────────────────────────────────────── */
const USUARIO_LABEL: Record<string, string> = {
  ...ROLE_LABEL,
  USER: 'Jefe de Área',
};

/* ─── Roles creables por el admin ───────────────────────────────────────────── */
const CREATABLE_ROLES: { value: Role; label: string }[] = [
  { value: 'USER', label: 'Jefe de Área' },
  { value: 'DPO',  label: 'DPO' },
];

/* ─── Helpers ────────────────────────────────────────────────────────────────── */
const generatePassword = () =>
  'LEY-' + Math.random().toString(36).slice(2, 8).toUpperCase();

/* ─── Estado del modal de confirmación ─────────────────────────────────────── */
type ConfirmAction =
  | { type: 'toggle'; user: AppUser }
  | { type: 'block';  user: AppUser };

/* ─── Componente principal ─────────────────────────────────────────────────── */
const UsuariosPage = () => {
  const internalUsers = MOCK_USERS.filter((u) => !u.roles.includes('TITULAR'));
  const [users, setUsers] = useState<AppUser[]>(internalUsers);

  // Dominios activos (para el combobox de área)
  const [activeDomains] = useState(() => MOCK_DOMAINS.filter((d) => d.active));

  /* ── Estado modal crear ──────────────────────────────────────────────────── */
  const [showCreate, setShowCreate]   = useState(false);
  const [selectedRole, setSelectedRole] = useState<Role>('USER');
  const [form, setForm] = useState({ name: '', email: '', domainId: '' });
  const [provisionalPwd, setProvisionalPwd] = useState<string | null>(null);

  /* ── Estado modal confirmación ───────────────────────────────────────────── */
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);

  /* ─── Crear usuario ─────────────────────────────────────────────────────── */
  const handleCreate = () => {
    if (!form.name.trim() || !form.email.trim()) return;
    if (selectedRole === 'USER' && !form.domainId) return;

    const domain = MOCK_DOMAINS.find((d) => d.id === form.domainId);
    const password = generatePassword();

    const newUser: AppUser = {
      id:       `u${Date.now()}`,
      name:     form.name.trim(),
      email:    form.email.trim(),
      password,
      roles:    [selectedRole],
      role:     selectedRole,
      area:     selectedRole === 'USER' ? (domain?.name ?? null) : null,
      active:   true,
      blocked:  false,
    };

    addMockUser(newUser);
    setUsers((prev) => [...prev, newUser]);
    setForm({ name: '', email: '', domainId: '' });
    setProvisionalPwd(password);
  };

  const handleCloseCreate = () => {
    setShowCreate(false);
    setProvisionalPwd(null);
    setForm({ name: '', email: '', domainId: '' });
    setSelectedRole('USER');
  };

  /* ─── Toggle activo / desactivado ───────────────────────────────────────── */
  const handleConfirmToggle = () => {
    if (!confirmAction || confirmAction.type !== 'toggle') return;
    const target = confirmAction.user;
    const nextActive = !target.active;
    updateMockUser(target.id, { active: nextActive });
    setUsers((prev) =>
      prev.map((u) => (u.id === target.id ? { ...u, active: nextActive } : u)),
    );
    setConfirmAction(null);
  };

  /* ─── Bloqueo permanente ────────────────────────────────────────────────── */
  const handleConfirmBlock = () => {
    if (!confirmAction || confirmAction.type !== 'block') return;
    updateMockUser(confirmAction.user.id, { blocked: true, active: false });
    setUsers((prev) =>
      prev.map((u) =>
        u.id === confirmAction.user.id ? { ...u, blocked: true, active: false } : u,
      ),
    );
    setConfirmAction(null);
  };

  /* ─── Guards para el formulario ─────────────────────────────────────────── */
  const noDomains       = activeDomains.length === 0;
  const createDisabled  =
    !form.name.trim() ||
    !form.email.trim() ||
    (selectedRole === 'USER' && (!form.domainId || noDomains));

  return (
    <div className={styles.page}>
      {/* ── Encabezado ───────────────────────────────────────────────────── */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Usuarios</h2>
          <p className={styles.subtitle}>Responsables y DPOs registrados en el sistema</p>
        </div>
        <div className={styles.headerActions}>
          <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
            + Agregar usuario
          </Button>
        </div>
      </div>

      {/* ── Tabla ────────────────────────────────────────────────────────── */}
      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Correo</th>
                <th>Rol</th>
                <th>Área</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <UserRow
                  key={u.id}
                  user={u}
                  onToggle={() => setConfirmAction({ type: 'toggle', user: u })}
                  onBlock={()  => setConfirmAction({ type: 'block',  user: u })}
                />
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* ── Modal: Crear usuario ──────────────────────────────────────────── */}
      <Modal open={showCreate} onClose={handleCloseCreate} variant="center">
        <div className={styles.modal}>
          {provisionalPwd ? (
            /* ── Vista de éxito ── */
            <>
              <div className={styles.modalHeader}>
                <h3 className={styles.modalTitle}>Usuario creado</h3>
                <button className={styles.closeBtn} onClick={handleCloseCreate}>✕</button>
              </div>
              <div className={styles.successBody}>
                <div className={styles.successIcon}>
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="10"/>
                    <polyline points="9 12 11 14 15 10"/>
                  </svg>
                </div>
                <p className={styles.successMsg}>
                  La cuenta ha sido creada. Comparte la contraseña provisoria con el usuario — deberá cambiarla en su primer acceso.
                </p>
                <div className={styles.passwordBox}>
                  <span className={styles.passwordLabel}>Contraseña provisoria</span>
                  <span className={styles.passwordValue}>{provisionalPwd}</span>
                </div>
                <p className={styles.passwordHint}>
                  En producción, esto se enviaría automáticamente al correo del usuario.
                </p>
              </div>
              <div className={styles.modalFooter}>
                <Button variant="primary" onClick={handleCloseCreate}>Entendido</Button>
              </div>
            </>
          ) : (
            /* ── Formulario ── */
            <>
              <div className={styles.modalHeader}>
                <h3 className={styles.modalTitle}>Agregar usuario</h3>
                <button className={styles.closeBtn} onClick={handleCloseCreate}>✕</button>
              </div>
              <p className={styles.modalHint}>
                Completa los datos para crear la cuenta. El rol determina los permisos del usuario.
              </p>

              <div className={styles.fields}>
                {/* 1. Nombre */}
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

                {/* 2. Correo */}
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

                {/* 3. Rol */}
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="u-role">Rol</label>
                  <select
                    id="u-role"
                    className={styles.select}
                    value={selectedRole}
                    onChange={(e) => {
                      setSelectedRole(e.target.value as Role);
                      setForm((f) => ({ ...f, domainId: '' }));
                    }}
                  >
                    {CREATABLE_ROLES.map((r) => (
                      <option key={r.value} value={r.value}>{r.label}</option>
                    ))}
                  </select>
                </div>

                {/* 4. Área (solo Jefe de Área) */}
                {selectedRole === 'USER' && (
                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel} htmlFor="u-domain">
                      Área asignada
                    </label>
                    {noDomains ? (
                      <div className={styles.noDomainWarning}>
                        <span className={styles.warnIcon}>⚠</span>
                        No hay dominios activos.{' '}
                        <a href="/dominios" className={styles.warnLink}>Crea al menos uno</a> antes de agregar un jefe de área.
                      </div>
                    ) : (
                      <select
                        id="u-domain"
                        className={styles.select}
                        value={form.domainId}
                        onChange={(e) => setForm((f) => ({ ...f, domainId: e.target.value }))}
                      >
                        <option value="">Selecciona un dominio...</option>
                        {activeDomains.map((d) => (
                          <option key={d.id} value={d.id}>{d.name}</option>
                        ))}
                      </select>
                    )}
                  </div>
                )}

                {/* Info: DPO sin área */}
                {selectedRole === 'DPO' && (
                  <p className={styles.roleInfo}>
                    El DPO tiene acceso global a todos los dominios. No se asigna a un área específica.
                  </p>
                )}
              </div>

              <div className={styles.modalFooter}>
                <Button variant="ghost" onClick={handleCloseCreate}>Cancelar</Button>
                <Button variant="primary" onClick={handleCreate} disabled={createDisabled}>
                  Crear usuario
                </Button>
              </div>
            </>
          )}
        </div>
      </Modal>

      {/* ── Modal: Confirmar toggle activo/desactivado ────────────────────── */}
      <Modal
        open={confirmAction?.type === 'toggle'}
        onClose={() => setConfirmAction(null)}
        variant="center"
      >
        {confirmAction?.type === 'toggle' && (
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>
                {confirmAction.user.active ? 'Desactivar usuario' : 'Reactivar usuario'}
              </h3>
              <button className={styles.closeBtn} onClick={() => setConfirmAction(null)}>✕</button>
            </div>

            <div className={styles.confirmBody}>
              <div className={[styles.confirmIcon, confirmAction.user.active ? styles.confirmWarn : styles.confirmInfo].join(' ')}>
                {confirmAction.user.active ? '⚠' : '↩'}
              </div>
              <div className={styles.confirmUserName}>{confirmAction.user.name}</div>
              <p className={styles.confirmText}>
                {confirmAction.user.active
                  ? <>¿Deseas <strong>desactivar temporalmente</strong> este usuario? No podrá iniciar sesión hasta que sea reactivado.</>
                  : <>¿Deseas <strong>reactivar</strong> este usuario? Podrá volver a iniciar sesión normalmente.</>
                }
              </p>
            </div>

            <div className={styles.modalFooter}>
              <Button variant="ghost" onClick={() => setConfirmAction(null)}>Cancelar</Button>
              <Button
                variant={confirmAction.user.active ? 'danger' : 'primary'}
                onClick={handleConfirmToggle}
              >
                {confirmAction.user.active ? 'Sí, desactivar' : 'Sí, reactivar'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* ── Modal: Confirmar bloqueo permanente ──────────────────────────── */}
      <Modal
        open={confirmAction?.type === 'block'}
        onClose={() => setConfirmAction(null)}
        variant="center"
      >
        {confirmAction?.type === 'block' && (
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 className={[styles.modalTitle, styles.dangerTitle].join(' ')}>
                Bloqueo permanente
              </h3>
              <button className={styles.closeBtn} onClick={() => setConfirmAction(null)}>✕</button>
            </div>

            <div className={styles.confirmBody}>
              <div className={[styles.confirmIcon, styles.confirmDanger].join(' ')}>🚫</div>
              <div className={styles.confirmUserName}>{confirmAction.user.name}</div>
              <div className={styles.dangerBox}>
                <strong>⚠ Esta acción bloqueará permanentemente el acceso del usuario.</strong>
                <p>El bloqueo no se puede revertir desde esta interfaz. Para desbloquear la cuenta, contacta al equipo técnico.</p>
              </div>
            </div>

            <div className={styles.modalFooter}>
              <Button variant="ghost" onClick={() => setConfirmAction(null)}>Cancelar</Button>
              <Button variant="danger" onClick={handleConfirmBlock}>
                Sí, bloquear permanentemente
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

/* ─── Fila de usuario ───────────────────────────────────────────────────────── */
interface UserRowProps {
  user: AppUser;
  onToggle: () => void;
  onBlock:  () => void;
}

const UserRow = ({ user, onToggle, onBlock }: UserRowProps) => {
  const isBlocked    = user.blocked;
  const isInactive   = !user.active && !user.blocked;
  const isActive     = user.active && !user.blocked;

  return (
    <tr className={isBlocked ? styles.rowBlocked : isInactive ? styles.rowInactive : ''}>
      {/* Usuario */}
      <td>
        <div className={styles.userCell}>
          <span className={[styles.avatar, styles[`avatar_${user.role.toLowerCase()}`]].join(' ')}>
            {initials(user.name)}
          </span>
          <span className={styles.userName}>{user.name}</span>
        </div>
      </td>

      {/* Correo */}
      <td className={styles.cellMono}>{user.email}</td>

      {/* Rol */}
      <td>
        <span className={[styles.roleBadge, styles[`role_${user.role.toLowerCase()}`]].join(' ')}>
          {USUARIO_LABEL[user.role]}
        </span>
      </td>

      {/* Área */}
      <td className={styles.cellArea}>
        {user.area ?? <span className={styles.noArea}>—</span>}
      </td>

      {/* Estado */}
      <td>
        {isBlocked  && <span className={[styles.statusBadge, styles.statusBlocked].join(' ')}>Bloqueado</span>}
        {isInactive && <span className={[styles.statusBadge, styles.statusInactive].join(' ')}>Desactivado</span>}
        {isActive   && <span className={[styles.statusBadge, styles.statusActive].join(' ')}>Activo</span>}
      </td>

      {/* Acciones */}
      <td>
        <div className={styles.actionsCell}>
          {/* Toggle activo/inactivo */}
          <button
            className={[styles.toggleBtn, isActive ? styles.toggleOn : styles.toggleOff].join(' ')}
            onClick={onToggle}
            disabled={isBlocked}
            title={isBlocked ? 'Cuenta bloqueada' : isActive ? 'Desactivar temporalmente' : 'Reactivar cuenta'}
            aria-label={isActive ? 'Desactivar usuario' : 'Reactivar usuario'}
          >
            <span className={styles.toggleThumb} />
          </button>

          {/* Bloqueo permanente */}
          <button
            className={styles.blockBtn}
            onClick={onBlock}
            disabled={isBlocked}
            title={isBlocked ? 'Ya está bloqueado' : 'Bloquear permanentemente'}
          >
            🚫
          </button>
        </div>
      </td>
    </tr>
  );
};

export default UsuariosPage;
