import { useState, useEffect } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import {
  getAllDomains,
  createDomain,
  deactivateDomain,
  reactivateDomain,
  ApiError,
} from '../api/domainsApi';
import type { DomainDto } from '../api/domainsApi';
import { getUsers } from '../api/usersApi';
import type { UserSummaryDto } from '../api/usersApi';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import { formatDate } from '../utils/formatters';
import styles from './DomainsPage.module.css';

const DomainsPage = () => {
  const { accessToken } = useAuth();

  const [domains, setDomains] = useState<DomainDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ code: '', name: '', description: '', jefeId: '' });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [jefeUsers, setJefeUsers] = useState<UserSummaryDto[]>([]);
  const [loadingJefes, setLoadingJefes] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFetchError(null);
    getAllDomains(accessToken)
      .then((data) => { if (!cancelled) setDomains(data); })
      .catch((err) => {
        if (!cancelled)
          setFetchError(err instanceof ApiError ? err.message : 'No se pudieron cargar los dominios');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  const handleOpenModal = async () => {
    setShowModal(true);
    setLoadingJefes(true);
    try {
      const users = await getUsers(accessToken);
      setJefeUsers(users.filter((u) => u.roles.includes('JEFE_DOMINIO')));
    } catch {
      setJefeUsers([]);
    } finally {
      setLoadingJefes(false);
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setForm({ code: '', name: '', description: '', jefeId: '' });
    setCreateError(null);
    setCreating(false);
  };

  const handleCreate = async () => {
    if (!form.code.trim() || !form.name.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      await createDomain(
        {
          code: form.code.trim(),
          name: form.name.trim(),
          description: form.description.trim(),
          jefeId: form.jefeId || null,
        },
        accessToken,
      );
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Error al crear el dominio');
      return;
    } finally {
      setCreating(false);
    }
    handleCloseModal();
    getAllDomains(accessToken).then(setDomains).catch(() => {});
  };

  const handleActionClick = (id: string) => {
    setConfirmingId(id);
    setActionError(null);
  };

  const handleActionCancel = () => setConfirmingId(null);

  const handleActionConfirm = async (domain: DomainDto) => {
    setActionLoading(domain.id);
    setConfirmingId(null);
    setActionError(null);
    try {
      if (domain.active ?? true) {
        await deactivateDomain(domain.id, accessToken);
      } else {
        await reactivateDomain(domain.id, accessToken);
      }
      setDomains((prev) =>
        prev.map((d) => (d.id === domain.id ? { ...d, active: !(d.active ?? true) } : d)),
      );
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Error al actualizar el dominio');
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Dominios</h2>
          <p className={styles.subtitle}>Áreas organizacionales registradas en el sistema</p>
        </div>
        <Button variant="primary" size="sm" onClick={handleOpenModal}>
          + Nuevo dominio
        </Button>
      </div>

      {fetchError && (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
          {fetchError}
        </p>
      )}

      {actionError && (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
          {actionError}
        </p>
      )}

      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {loading ? (
            <p style={{ padding: '24px 16px', margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
              Cargando dominios…
            </p>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Nombre</th>
                  <th>Descripción</th>
                  <th>Estado</th>
                  <th>Creado el</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {domains.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ padding: '24px 16px', color: 'var(--color-text-muted)' }}>
                      No hay dominios registrados.
                    </td>
                  </tr>
                ) : (
                  domains.map((domain) => {
                    const isActive = domain.active ?? false;
                    const isConfirming = confirmingId === domain.id;
                    const isProcessing = actionLoading === domain.id;

                    return (
                      <tr
                        key={domain.id}
                        className={!isActive ? styles.rowInactive : ''}
                      >
                        <td>
                          <span className={styles.codeCell}>{domain.code}</span>
                        </td>
                        <td className={styles.nameCell}>{domain.name}</td>
                        <td>
                          {domain.description
                            ? <span className={styles.descCell}>{domain.description}</span>
                            : <span className={styles.noDesc}>—</span>
                          }
                        </td>
                        <td>
                          <span className={[
                            styles.statusBadge,
                            isActive ? styles.status_active : styles.status_inactive,
                          ].join(' ')}>
                            {isActive ? 'Activo' : 'Inactivo'}
                          </span>
                        </td>
                        <td className={styles.dateCell}>
                          {formatDate(domain.createdAt)}
                        </td>
                        <td className={styles.actionsCell}>
                          {isConfirming ? (
                            <div className={styles.confirmInline}>
                              <span className={styles.confirmText}>¿Confirmar?</span>
                              <button
                                className={styles.confirmYes}
                                onClick={() => handleActionConfirm(domain)}
                              >
                                Sí
                              </button>
                              <button
                                className={styles.confirmNo}
                                onClick={handleActionCancel}
                              >
                                No
                              </button>
                            </div>
                          ) : (
                            <Button
                              variant={isActive ? 'ghost' : 'primary'}
                              size="sm"
                              onClick={() => handleActionClick(domain.id)}
                              disabled={isProcessing}
                            >
                              {isProcessing
                                ? (isActive ? 'Desactivando…' : 'Reactivando…')
                                : (isActive ? 'Desactivar' : 'Reactivar')}
                            </Button>
                          )}
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

      <Modal open={showModal} onClose={handleCloseModal} variant="center">
        <div className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>Nuevo dominio</h3>
            <button className={styles.closeBtn} onClick={handleCloseModal}>✕</button>
          </div>

          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="d-code">Código</label>
              <input
                id="d-code"
                type="text"
                className={styles.input}
                placeholder="Ej: mkt, legal, rrhh"
                value={form.code}
                onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
              />
              <p className={styles.fieldHint}>Identificador único, sin espacios ni caracteres especiales.</p>
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="d-name">Nombre</label>
              <input
                id="d-name"
                type="text"
                className={styles.input}
                placeholder="Ej: Marketing, Legal, Recursos Humanos"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="d-desc">Descripción <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>(opcional)</span></label>
              <textarea
                id="d-desc"
                className={styles.textarea}
                placeholder="Descripción del área y su rol en la organización"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="d-jefe">
                Responsable asignado <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>(opcional)</span>
              </label>
              <select
                id="d-jefe"
                className={styles.select}
                value={form.jefeId}
                onChange={(e) => setForm((f) => ({ ...f, jefeId: e.target.value }))}
                disabled={loadingJefes}
              >
                <option value="">{loadingJefes ? 'Cargando…' : 'Sin asignar'}</option>
                {jefeUsers.map((u) => (
                  <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
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
              disabled={!form.code.trim() || !form.name.trim() || creating}
            >
              {creating ? 'Creando…' : 'Crear dominio'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default DomainsPage;
