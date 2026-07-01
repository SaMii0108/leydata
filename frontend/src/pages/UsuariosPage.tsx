import { useState, useEffect } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import {
  getUsers, getUser, createUser, updateUser,
  blockUser, deactivateUser, reactivateUser, ApiError,
} from '../api/usersApi';
import type { UserSummaryDto, UpdateUserPayload } from '../api/usersApi';
import { getAllDomains } from '../api/domainsApi';
import type { DomainDto } from '../api/domainsApi';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import { ROLE_LABEL } from '../constants/labels';
import { initials } from '../utils/formatters';
import styles from './UsuariosPage.module.css';

const USUARIO_LABEL: Record<string, string> = {
  ...ROLE_LABEL,
  JEFE_DOMINIO: 'Responsable de área',
};

const ROLE_OPTIONS = [
  { value: 'ADMIN',        label: 'Administrador' },
  { value: 'DPO',          label: 'DPO' },
  { value: 'JEFE_DOMINIO', label: 'Responsable de área' },
] as const;

const generatePassword = () => 'LEY-' + Math.random().toString(36).slice(2, 8).toUpperCase();

const primaryRole = (u: UserSummaryDto): string => u.roles[0] ?? '';
const primaryArea = (u: UserSummaryDto): string | null => u.domains[0]?.name ?? null;

type StatusVariant = 'active' | 'inactive' | 'blocked';
const statusInfo = (u: UserSummaryDto): { label: string; variant: StatusVariant } => {
  if (u.blocked) return { label: 'Bloqueado', variant: 'blocked' };
  if (!u.active)  return { label: 'Inactivo',  variant: 'inactive' };
  return { label: 'Activo', variant: 'active' };
};

interface CreateForm { name: string; email: string; roleCode: string; domainIds: string[]; }
interface EditForm   { name: string; email: string; roleCode: string; domainIds: string[]; newPassword: string; }

const UsuariosPage = () => {
  const { accessToken, user: currentUser } = useAuth();

  // ── Data ──────────────────────────────────────────────────────────────────
  const [users,      setUsers]      = useState<UserSummaryDto[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [domains,    setDomains]    = useState<DomainDto[]>([]);

  // ── Create modal ──────────────────────────────────────────────────────────
  const [showCreate,         setShowCreate]         = useState(false);
  const [createForm,         setCreateForm]         = useState<CreateForm>({ name: '', email: '', roleCode: 'JEFE_DOMINIO', domainIds: [] });
  const [creating,           setCreating]           = useState(false);
  const [createError,        setCreateError]        = useState<string | null>(null);
  const [provisionalPassword, setProvisionalPassword] = useState<string | null>(null);

  // ── Edit drawer ───────────────────────────────────────────────────────────
  const [editingUser,  setEditingUser]  = useState<UserSummaryDto | null>(null);
  const [editForm,     setEditForm]     = useState<EditForm>({ name: '', email: '', roleCode: '', domainIds: [], newPassword: '' });
  const [saving,       setSaving]       = useState(false);
  const [saveError,    setSaveError]    = useState<string | null>(null);
  const [saveSuccess,  setSaveSuccess]  = useState(false);

  // ── Status actions ────────────────────────────────────────────────────────
  const [confirmDeactivate, setConfirmDeactivate] = useState<UserSummaryDto | null>(null);
  const [confirmBlock,      setConfirmBlock]      = useState<UserSummaryDto | null>(null);
  const [actionLoading,     setActionLoading]     = useState<string | null>(null);
  const [actionError,       setActionError]       = useState<string | null>(null);

  useEffect(() => {
    if (!actionError) return;
    const t = setTimeout(() => setActionError(null), 5000);
    return () => clearTimeout(t);
  }, [actionError]);

  // ── Load data ─────────────────────────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFetchError(null);
    Promise.all([getUsers(accessToken), getAllDomains(accessToken)])
      .then(([usersData, domainsData]) => {
        if (!cancelled) {
          setUsers(usersData.filter((u) => !u.roles.every((r) => r === 'TITULAR')));
          setDomains(domainsData.filter((d) => d.active));
        }
      })
      .catch((err) => {
        if (!cancelled)
          setFetchError(err instanceof ApiError ? err.message : 'No se pudieron cargar los datos');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  // ── Create handlers ───────────────────────────────────────────────────────
  const handleCreate = async () => {
    if (!createForm.name.trim() || !createForm.email.trim()) return;
    const password = generatePassword();
    setCreating(true);
    setCreateError(null);
    try {
      const keycloakId = await createUser(
        {
          name:      createForm.name.trim(),
          email:     createForm.email.trim(),
          password,
          roleCode:  createForm.roleCode,
          domainIds: createForm.roleCode === 'JEFE_DOMINIO' ? createForm.domainIds : undefined,
        },
        accessToken,
      );
      setProvisionalPassword(password);
      setCreateForm({ name: '', email: '', roleCode: 'JEFE_DOMINIO', domainIds: [] });
      getUser(keycloakId, accessToken)
        .then((created) => setUsers((prev) => [...prev, created]))
        .catch(() => {});
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Error al crear el usuario');
    } finally {
      setCreating(false);
    }
  };

  const closeCreate = () => {
    setShowCreate(false);
    setProvisionalPassword(null);
    setCreateError(null);
    setCreateForm({ name: '', email: '', roleCode: 'JEFE_DOMINIO', domainIds: [] });
  };

  // ── Edit handlers ─────────────────────────────────────────────────────────
  const openEdit = (u: UserSummaryDto) => {
    setEditingUser(u);
    setEditForm({
      name:        u.name,
      email:       u.email,
      roleCode:    primaryRole(u),
      domainIds:   u.domains.map((d) => d.id),
      newPassword: '',
    });
    setSaveError(null);
    setSaveSuccess(false);
    setActionError(null);
  };

  const closeEdit = () => {
    setEditingUser(null);
    setSaveError(null);
    setSaveSuccess(false);
  };

  const handleSave = async () => {
    if (!editingUser || !editForm.name.trim() || !editForm.email.trim()) return;
    setSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const selfEdit = editingUser.keycloakId === currentUser?.id;
      const payload: UpdateUserPayload = {
        name:  editForm.name.trim(),
        email: editForm.email.trim(),
      };
      if (!selfEdit) {
        payload.roleCodes = [editForm.roleCode];
        payload.domainIds = editForm.roleCode === 'JEFE_DOMINIO' ? editForm.domainIds : [];
        if (editForm.newPassword.trim()) payload.password = editForm.newPassword.trim();
      }
      const updated = await updateUser(editingUser.keycloakId, payload, accessToken);
      setUsers((prev) => prev.map((u) => (u.keycloakId === updated.keycloakId ? updated : u)));
      setEditingUser(updated);
      setEditForm((f) => ({ ...f, newPassword: '' }));
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'No se pudo guardar el usuario');
    } finally {
      setSaving(false);
    }
  };

  // ── Status action handlers ────────────────────────────────────────────────
  const handleDeactivate = async (u: UserSummaryDto) => {
    setActionLoading(u.keycloakId);
    setActionError(null);
    try {
      const updated = await deactivateUser(u.keycloakId, accessToken);
      setUsers((prev) => prev.map((pu) => (pu.keycloakId === updated.keycloakId ? updated : pu)));
      if (editingUser?.keycloakId === updated.keycloakId) setEditingUser(updated);
      setConfirmDeactivate(null);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al desactivar el usuario');
      setConfirmDeactivate(null);
    } finally {
      setActionLoading(null);
    }
  };

  const handleReactivate = async (u: UserSummaryDto) => {
    setActionLoading(u.keycloakId);
    setActionError(null);
    try {
      const updated = await reactivateUser(u.keycloakId, accessToken);
      setUsers((prev) => prev.map((pu) => (pu.keycloakId === updated.keycloakId ? updated : pu)));
      if (editingUser?.keycloakId === updated.keycloakId) setEditingUser(updated);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al reactivar el usuario');
    } finally {
      setActionLoading(null);
    }
  };

  const handleBlock = async (u: UserSummaryDto) => {
    setActionLoading(u.keycloakId);
    setActionError(null);
    try {
      const updated = await blockUser(u.keycloakId, accessToken);
      setUsers((prev) => prev.map((pu) => (pu.keycloakId === updated.keycloakId ? updated : pu)));
      if (editingUser?.keycloakId === updated.keycloakId) setEditingUser(updated);
      setConfirmBlock(null);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al bloquear el usuario');
      setConfirmBlock(null);
    } finally {
      setActionLoading(null);
    }
  };

  // ── Guards ────────────────────────────────────────────────────────────────
  const isSelf  = (u: UserSummaryDto) => u.keycloakId === currentUser?.id;
  const isAdmin = (u: UserSummaryDto) => u.roles.includes('ADMIN');

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Usuarios</h2>
          <p className={styles.subtitle}>Gestiona los usuarios operativos del sistema</p>
        </div>
        <div className={styles.headerActions}>
          <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
            + Nuevo usuario
          </Button>
        </div>
      </div>

      {fetchError  && <p className={styles.errorMsg}>{fetchError}</p>}
      {actionError && <p className={styles.errorMsg}>{actionError}</p>}

      {/* ── Table ──────────────────────────────────────────────────────────── */}
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
                  <th>Estado</th>
                  <th className={styles.actionsTh}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {users.length === 0 ? (
                  <tr className={styles.emptyRow}>
                    <td colSpan={6}>No hay usuarios registrados</td>
                  </tr>
                ) : (
                  users.map((u) => {
                    const st      = statusInfo(u);
                    const rowBusy = actionLoading === u.keycloakId;
                    return (
                      <tr key={u.keycloakId}>
                        <td>
                          <div className={styles.userCell}>
                            <span className={[styles.avatar, styles[`avatar_${primaryRole(u).toLowerCase()}`]].join(' ')}>
                              {initials(u.name)}
                            </span>
                            <span className={styles.userName}>{u.name}</span>
                          </div>
                        </td>
                        <td className={styles.cellMono}>{u.email}</td>
                        <td>
                          <span className={[styles.roleBadge, styles[`role_${primaryRole(u).toLowerCase()}`]].join(' ')}>
                            {USUARIO_LABEL[primaryRole(u)] ?? primaryRole(u)}
                          </span>
                        </td>
                        <td className={styles.cellArea}>
                          {primaryArea(u) ?? <span className={styles.noArea}>—</span>}
                        </td>
                        <td>
                          <span className={[styles.statusBadge, styles[`status_${st.variant}`]].join(' ')}>
                            {st.label}
                          </span>
                        </td>
                        <td className={styles.actionsCell}>
                          <div className={styles.tableActions}>
                            <button className={styles.actionBtn} onClick={() => openEdit(u)}>
                              Editar
                            </button>
                            {!u.blocked && u.active && (
                              <button
                                className={styles.actionBtn}
                                onClick={() => setConfirmDeactivate(u)}
                                disabled={rowBusy || isSelf(u) || isAdmin(u)}
                                title={
                                  isSelf(u)  ? 'No puedes desactivarte a ti mismo' :
                                  isAdmin(u) ? 'Los administradores no pueden desactivarse' :
                                  undefined
                                }
                              >
                                Desactivar
                              </button>
                            )}
                            {!u.blocked && !u.active && (
                              <button
                                className={styles.actionBtn}
                                onClick={() => handleReactivate(u)}
                                disabled={rowBusy}
                              >
                                {rowBusy ? '…' : 'Reactivar'}
                              </button>
                            )}
                            {!u.blocked && !isSelf(u) && !isAdmin(u) && (
                              <button
                                className={styles.actionBtnDanger}
                                onClick={() => setConfirmBlock(u)}
                                disabled={rowBusy}
                              >
                                Bloquear
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          )}
        </div>
      </section>

      {/* ── Create modal ────────────────────────────────────────────────────── */}
      <Modal open={showCreate} onClose={closeCreate} variant="center">
        <div className={styles.modal}>
          {provisionalPassword ? (
            <>
              <div className={styles.modalHeader}>
                <h3 className={styles.modalTitle}>Usuario creado</h3>
                <button className={styles.closeBtn} onClick={closeCreate}>✕</button>
              </div>
              <div className={styles.successBody}>
                <div className={styles.successIcon}>
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="9 12 11 14 15 10" />
                  </svg>
                </div>
                <p className={styles.successMsg}>
                  La cuenta ha sido creada. Comparte la contraseña provisoria con el usuario.
                </p>
                <div className={styles.passwordBox}>
                  <span className={styles.passwordLabel}>Contraseña provisoria</span>
                  <span className={styles.passwordValue}>{provisionalPassword}</span>
                </div>
                <p className={styles.passwordHint}>
                  En producción se enviaría automáticamente al correo del usuario.
                </p>
              </div>
              <div className={styles.modalFooter}>
                <Button variant="primary" onClick={closeCreate}>Entendido</Button>
              </div>
            </>
          ) : (
            <>
              <div className={styles.modalHeader}>
                <h3 className={styles.modalTitle}>Nuevo usuario</h3>
                <button className={styles.closeBtn} onClick={closeCreate}>✕</button>
              </div>

              <div className={styles.fields}>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="c-name">Nombre completo</label>
                  <input
                    id="c-name"
                    type="text"
                    className={styles.input}
                    placeholder="Ej: Juan Pérez González"
                    value={createForm.name}
                    onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))}
                  />
                </div>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="c-email">Correo electrónico</label>
                  <input
                    id="c-email"
                    type="email"
                    className={styles.input}
                    placeholder="correo@empresa.cl"
                    value={createForm.email}
                    onChange={(e) => setCreateForm((f) => ({ ...f, email: e.target.value }))}
                  />
                </div>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="c-role">Rol</label>
                  <select
                    id="c-role"
                    className={styles.select}
                    value={createForm.roleCode}
                    onChange={(e) => setCreateForm((f) => ({ ...f, roleCode: e.target.value, domainIds: [] }))}
                  >
                    {ROLE_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>{opt.label}</option>
                    ))}
                  </select>
                </div>
                {createForm.roleCode === 'JEFE_DOMINIO' && domains.length > 0 && (
                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel}>Áreas asignadas</label>
                    <div className={styles.checkboxList}>
                      {domains.map((d) => (
                        <label key={d.id} className={styles.checkboxItem}>
                          <input
                            type="checkbox"
                            checked={createForm.domainIds.includes(d.id)}
                            onChange={(e) =>
                              setCreateForm((f) => ({
                                ...f,
                                domainIds: e.target.checked
                                  ? [...f.domainIds, d.id]
                                  : f.domainIds.filter((id) => id !== d.id),
                              }))
                            }
                          />
                          {d.name}
                        </label>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {createError && <p className={styles.errorMsg}>{createError}</p>}

              <div className={styles.modalFooter}>
                <Button variant="ghost" onClick={closeCreate}>Cancelar</Button>
                <Button
                  variant="primary"
                  onClick={handleCreate}
                  disabled={!createForm.name.trim() || !createForm.email.trim() || creating}
                >
                  {creating ? 'Creando…' : 'Crear usuario'}
                </Button>
              </div>
            </>
          )}
        </div>
      </Modal>

      {/* ── Edit drawer ─────────────────────────────────────────────────────── */}
      {editingUser !== null && (
        <Modal open onClose={closeEdit} variant="drawer">
          <div className={styles.drawer}>
            <div className={styles.drawerHeader}>
              <div className={styles.drawerUserInfo}>
                <span className={[styles.avatar, styles[`avatar_${primaryRole(editingUser).toLowerCase()}`]].join(' ')}>
                  {initials(editingUser.name)}
                </span>
                <div>
                  <div className={styles.drawerUserName}>{editingUser.name}</div>
                  <div className={styles.drawerUserEmail}>{editingUser.email}</div>
                </div>
              </div>
              <button className={styles.closeBtn} onClick={closeEdit}>✕</button>
            </div>

            <div className={styles.drawerBody}>
              {saveSuccess && (
                <p className={styles.successBanner} style={{ margin: '16px 24px 0', borderRadius: 'var(--radius-sm)' }}>
                  ✓ Cambios guardados
                </p>
              )}
              {saveError && (
                <p className={styles.errorMsg} style={{ margin: '16px 24px 0', borderRadius: 'var(--radius-sm)' }}>
                  {saveError}
                </p>
              )}

              {/* Información básica */}
              <div className={styles.drawerSection}>
                <h4 className={styles.sectionTitle}>Información básica</h4>
                <div className={styles.fields}>
                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel} htmlFor="e-name">Nombre completo</label>
                    <input
                      id="e-name"
                      type="text"
                      className={styles.input}
                      value={editForm.name}
                      onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                    />
                  </div>
                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel} htmlFor="e-email">Correo electrónico</label>
                    <input
                      id="e-email"
                      type="email"
                      className={styles.input}
                      value={editForm.email}
                      onChange={(e) => setEditForm((f) => ({ ...f, email: e.target.value }))}
                    />
                  </div>
                </div>
              </div>

              {/* Rol */}
              <div className={styles.drawerSection}>
                <h4 className={styles.sectionTitle}>Rol</h4>
                {isSelf(editingUser) ? (
                  <p className={styles.noteText} style={{ margin: 0 }}>
                    No puedes cambiar tu propio rol para proteger el acceso al sistema.
                  </p>
                ) : (
                  <select
                    className={styles.select}
                    value={editForm.roleCode}
                    onChange={(e) => setEditForm((f) => ({ ...f, roleCode: e.target.value, domainIds: [] }))}
                  >
                    {ROLE_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>{opt.label}</option>
                    ))}
                  </select>
                )}
              </div>

              {/* Dominios — solo para JEFE_DOMINIO y cuando no es self */}
              {!isSelf(editingUser) && editForm.roleCode === 'JEFE_DOMINIO' && (
                <div className={styles.drawerSection}>
                  <h4 className={styles.sectionTitle}>Áreas asignadas</h4>
                  {domains.length === 0 ? (
                    <p className={styles.noteText} style={{ margin: 0 }}>No hay áreas activas disponibles.</p>
                  ) : (
                    <div className={styles.checkboxList}>
                      {domains.map((d) => (
                        <label key={d.id} className={styles.checkboxItem}>
                          <input
                            type="checkbox"
                            checked={editForm.domainIds.includes(d.id)}
                            onChange={(e) =>
                              setEditForm((f) => ({
                                ...f,
                                domainIds: e.target.checked
                                  ? [...f.domainIds, d.id]
                                  : f.domainIds.filter((id) => id !== d.id),
                              }))
                            }
                          />
                          {d.name}
                        </label>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Contraseña — no disponible para self */}
              {!isSelf(editingUser) && (
                <div className={styles.drawerSection}>
                  <h4 className={styles.sectionTitle}>Resetear contraseña</h4>
                  <div className={styles.fieldGroup}>
                    <input
                      type="password"
                      className={styles.input}
                      placeholder="Dejar vacío para no cambiar"
                      value={editForm.newPassword}
                      onChange={(e) => setEditForm((f) => ({ ...f, newPassword: e.target.value }))}
                    />
                    {editForm.newPassword && (
                      <p className={styles.noteText}>
                        El usuario deberá cambiar esta contraseña al iniciar sesión.
                      </p>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className={styles.drawerFooter}>
              <Button variant="ghost" onClick={closeEdit}>Cancelar</Button>
              <Button
                variant="primary"
                onClick={handleSave}
                disabled={saving || !editForm.name.trim() || !editForm.email.trim()}
              >
                {saving ? 'Guardando…' : 'Guardar cambios'}
              </Button>
            </div>

            {/* Danger zone — no disponible para self */}
            {!isSelf(editingUser) && (
              <div className={styles.dangerZone}>
                <p className={styles.dangerTitle}>Acciones de cuenta</p>
                <div className={styles.dangerActions}>
                  {editingUser.blocked ? (
                    <p className={styles.noteText} style={{ margin: 0 }}>
                      Este usuario está bloqueado permanentemente y no puede ser desbloqueado desde esta interfaz.
                    </p>
                  ) : isAdmin(editingUser) ? (
                    <p className={styles.noteText} style={{ margin: 0 }}>
                      Los administradores no pueden ser desactivados ni bloqueados.
                    </p>
                  ) : (
                    <>
                      {editingUser.active ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => { closeEdit(); setConfirmDeactivate(editingUser); }}
                          disabled={actionLoading === editingUser.keycloakId}
                        >
                          Desactivar cuenta
                        </Button>
                      ) : (
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => handleReactivate(editingUser)}
                          disabled={actionLoading === editingUser.keycloakId}
                        >
                          {actionLoading === editingUser.keycloakId ? 'Reactivando…' : 'Reactivar cuenta'}
                        </Button>
                      )}
                      <Button
                        variant="danger"
                        size="sm"
                        onClick={() => { closeEdit(); setConfirmBlock(editingUser); }}
                        disabled={actionLoading === editingUser.keycloakId}
                      >
                        Bloquear permanentemente
                      </Button>
                    </>
                  )}
                </div>
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* ── Confirm Deactivate ───────────────────────────────────────────────── */}
      <Modal open={confirmDeactivate !== null} onClose={() => setConfirmDeactivate(null)} variant="center">
        {confirmDeactivate && (
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Desactivar cuenta</h3>
              <button className={styles.closeBtn} onClick={() => setConfirmDeactivate(null)}>✕</button>
            </div>
            <p className={styles.modalHint}>
              ¿Desactivar la cuenta de <strong>{confirmDeactivate.name}</strong>?
              El usuario no podrá iniciar sesión hasta que sea reactivado.
            </p>
            <div className={styles.modalFooter}>
              <Button variant="ghost" onClick={() => setConfirmDeactivate(null)}>Cancelar</Button>
              <Button
                variant="secondary"
                onClick={() => handleDeactivate(confirmDeactivate)}
                disabled={actionLoading === confirmDeactivate.keycloakId}
              >
                {actionLoading === confirmDeactivate.keycloakId ? 'Desactivando…' : 'Sí, desactivar'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* ── Confirm Block ────────────────────────────────────────────────────── */}
      <Modal open={confirmBlock !== null} onClose={() => setConfirmBlock(null)} variant="center">
        {confirmBlock && (
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Bloquear usuario</h3>
              <button className={styles.closeBtn} onClick={() => setConfirmBlock(null)}>✕</button>
            </div>
            <p className={styles.modalHint}>
              ¿Bloquear permanentemente a <strong>{confirmBlock.name}</strong>?
            </p>
            <p className={[styles.modalHint, styles.dangerNote].join(' ')}>
              Esta acción es <strong>irreversible</strong>. El usuario no podrá acceder al sistema
              y no puede ser desbloqueado desde esta interfaz.
            </p>
            <div className={styles.modalFooter}>
              <Button variant="ghost" onClick={() => setConfirmBlock(null)}>Cancelar</Button>
              <Button
                variant="danger"
                onClick={() => handleBlock(confirmBlock)}
                disabled={actionLoading === confirmBlock.keycloakId}
              >
                {actionLoading === confirmBlock.keycloakId ? 'Bloqueando…' : 'Sí, bloquear'}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default UsuariosPage;
