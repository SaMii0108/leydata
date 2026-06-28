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
import { getUsers, updateUser } from '../api/usersApi';
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

  const [allUsers, setAllUsers] = useState<UserSummaryDto[]>([]);

  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ code: '', name: '', description: '' });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const [assignDomain, setAssignDomain] = useState<DomainDto | null>(null);
  const [assignSelectedJefeId, setAssignSelectedJefeId] = useState('');
  const [assigning, setAssigning] = useState(false);
  const [assignError, setAssignError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFetchError(null);
    Promise.all([getAllDomains(accessToken), getUsers(accessToken)])
      .then(([domainsData, usersData]) => {
        if (!cancelled) {
          setDomains(domainsData);
          setAllUsers(usersData);
        }
      })
      .catch((err) => {
        if (!cancelled)
          setFetchError(err instanceof ApiError ? err.message : 'No se pudieron cargar los datos');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  // Map: domain name → domain id (DomainDto.id)
  // Used to convert UserSummaryDto.domains (names) → domain UUIDs for PUT /api/users/{keycloakId}
  const domainNameToId = new Map(domains.map((d) => [d.name, d.id]));

  // Map: domain id (DomainDto.id) → the JEFE_DOMINIO user assigned to it
  const domainJefeMap = new Map<string, UserSummaryDto>();
  for (const user of allUsers) {
    if (!user.keycloakId || !user.roles.includes('JEFE_DOMINIO')) continue;
    for (const domainName of user.domains) {
      const domainId = domainNameToId.get(domainName);
      if (domainId) domainJefeMap.set(domainId, user);
    }
  }

  const jefeUsers = allUsers.filter(
    (u) => u.roles.includes('JEFE_DOMINIO') && Boolean(u.keycloakId),
  );

  const handleOpenModal = () => setShowModal(true);

  const handleCloseModal = () => {
    setShowModal(false);
    setForm({ code: '', name: '', description: '' });
    setCreateError(null);
    setCreating(false);
  };

  const handleCreate = async () => {
    if (!form.code.trim() || !form.name.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      await createDomain(
        { code: form.code.trim(), name: form.name.trim(), description: form.description.trim() },
        accessToken,
      );
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Error al crear el dominio');
      return;
    } finally {
      setCreating(false);
    }
    handleCloseModal();
    Promise.all([getAllDomains(accessToken), getUsers(accessToken)])
      .then(([domainsData, usersData]) => { setDomains(domainsData); setAllUsers(usersData); })
      .catch(() => {});
  };

  const handleActionClick = (id: string) => { setConfirmingId(id); setActionError(null); };
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

  const handleAssignOpen = (domain: DomainDto) => {
    const currentJefe = domainJefeMap.get(domain.id);
    setAssignDomain(domain);
    setAssignSelectedJefeId(currentJefe?.keycloakId ?? '');
    setAssignError(null);
  };

  const handleAssignClose = () => {
    setAssignDomain(null);
    setAssignSelectedJefeId('');
    setAssignError(null);
    setAssigning(false);
  };

  const handleAssignSave = async () => {
    if (!assignDomain) return;
    setAssigning(true);
    setAssignError(null);

    const currentJefe = domainJefeMap.get(assignDomain.id);
    const newJefeId = assignSelectedJefeId || null;

    if ((currentJefe?.keycloakId ?? null) === newJefeId) {
      handleAssignClose();
      return;
    }

    try {
      if (currentJefe?.keycloakId) {
        // Verify all current domain names of the old jefe can be resolved to UUIDs.
        // If any name is unresolvable, we would silently drop that domain — abort instead.
        const unresolvable = currentJefe.domains.filter((name) => !domainNameToId.has(name));
        if (unresolvable.length > 0) {
          setAssignError(
            `No se puede actualizar: dominios no resolubles (${unresolvable.join(', ')}). Recarga la página.`,
          );
          return;
        }
        const remainingDomainIds = currentJefe.domains
          .map((name) => domainNameToId.get(name))
          .filter((id): id is string => Boolean(id) && id !== assignDomain.id);
        await updateUser(currentJefe.keycloakId, { domainIds: remainingDomainIds }, accessToken);
      }

      if (newJefeId) {
        const newJefe = allUsers.find((u) => u.keycloakId === newJefeId);
        if (newJefe) {
          // Same defensive check for the new jefe's existing domains.
          const unresolvable = newJefe.domains.filter((name) => !domainNameToId.has(name));
          if (unresolvable.length > 0) {
            setAssignError(
              `No se puede actualizar: dominios no resolubles (${unresolvable.join(', ')}). Recarga la página.`,
            );
            return;
          }
          const currentDomainIds = newJefe.domains
            .map((name) => domainNameToId.get(name))
            .filter((id): id is string => Boolean(id));
          const updatedDomainIds = currentDomainIds.includes(assignDomain.id)
            ? currentDomainIds
            : [...currentDomainIds, assignDomain.id];
          await updateUser(newJefeId, { domainIds: updatedDomainIds }, accessToken);
        }
      }

      const usersData = await getUsers(accessToken);
      setAllUsers(usersData);
      handleAssignClose();
    } catch (err) {
      setAssignError(err instanceof ApiError ? err.message : 'Error al asignar el responsable');
      getUsers(accessToken).then(setAllUsers).catch(() => {});
    } finally {
      setAssigning(false);
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
                  <th>Responsable</th>
                  <th>Estado</th>
                  <th>Creado el</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {domains.length === 0 ? (
                  <tr>
                    <td colSpan={7} style={{ padding: '24px 16px', color: 'var(--color-text-muted)' }}>
                      No hay dominios registrados.
                    </td>
                  </tr>
                ) : (
                  domains.map((domain) => {
                    const isActive = domain.active ?? false;
                    const isConfirming = confirmingId === domain.id;
                    const isProcessing = actionLoading === domain.id;
                    const jefe = domainJefeMap.get(domain.id);

                    return (
                      <tr key={domain.id} className={!isActive ? styles.rowInactive : ''}>
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
                          {jefe
                            ? <span className={styles.jefeCell}>{jefe.name}</span>
                            : <span className={styles.noDesc}>Sin asignar</span>
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
                        <td className={styles.dateCell}>{formatDate(domain.createdAt)}</td>
                        <td className={styles.actionsCell}>
                          {isConfirming ? (
                            <div className={styles.confirmInline}>
                              <span className={styles.confirmText}>¿Confirmar?</span>
                              <button className={styles.confirmYes} onClick={() => handleActionConfirm(domain)}>
                                Sí
                              </button>
                              <button className={styles.confirmNo} onClick={handleActionCancel}>
                                No
                              </button>
                            </div>
                          ) : (
                            <div className={styles.actionsGroup}>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleAssignOpen(domain)}
                              >
                                {jefe ? 'Cambiar responsable' : 'Asignar responsable'}
                              </Button>
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
                            </div>
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
              <label className={styles.fieldLabel} htmlFor="d-desc">
                Descripción <span style={{ fontWeight: 400, color: 'var(--color-text-muted)' }}>(opcional)</span>
              </label>
              <textarea
                id="d-desc"
                className={styles.textarea}
                placeholder="Descripción del área y su rol en la organización"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
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

      <Modal open={assignDomain !== null} onClose={handleAssignClose} variant="center">
        <div className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>Responsable del dominio</h3>
            <button className={styles.closeBtn} onClick={handleAssignClose}>✕</button>
          </div>

          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel}>Dominio</label>
              <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
                {assignDomain?.name}
                {assignDomain?.code && (
                  <span className={styles.codeCell} style={{ marginLeft: 8 }}>{assignDomain.code}</span>
                )}
              </p>
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="a-jefe">Responsable asignado</label>
              <select
                id="a-jefe"
                className={styles.select}
                value={assignSelectedJefeId}
                onChange={(e) => setAssignSelectedJefeId(e.target.value)}
                disabled={assigning}
              >
                <option value="">Sin asignar</option>
                {jefeUsers.map((u) => (
                  <option key={u.keycloakId} value={u.keycloakId}>{u.name} ({u.email})</option>
                ))}
              </select>
            </div>
          </div>

          {assignError && (
            <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
              {assignError}
            </p>
          )}

          <div className={styles.modalFooter}>
            <Button variant="ghost" onClick={handleAssignClose} disabled={assigning}>Cancelar</Button>
            <Button variant="primary" onClick={handleAssignSave} disabled={assigning}>
              {assigning ? 'Guardando…' : 'Guardar'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default DomainsPage;
