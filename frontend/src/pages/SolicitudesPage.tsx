import { useState, useMemo } from 'react';
import { MOCK_REQUESTS, addMockRequest, reviewMockRequest } from '../features/requests/mockRequests';
import type { PurposeRequest, RequestStatus } from '../features/requests/mockRequests';
import { MOCK_DOMAINS } from '../features/domains/mockDomains';
import { useAuth } from '../features/auth/useAuth';
import { usePermissions } from '../features/auth/usePermissions';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import { formatDate } from '../utils/formatters';
import styles from './SolicitudesPage.module.css';

/* ─── Badge de estado ────────────────────────────────────────────────────────── */
const STATUS_LABEL: Record<RequestStatus, string> = {
  PENDING:  'Pendiente',
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada',
};

const StatusBadge = ({ status }: { status: RequestStatus }) => (
  <span className={[styles.statusBadge, styles[`status_${status.toLowerCase()}`]].join(' ')}>
    {STATUS_LABEL[status]}
  </span>
);

/* ─── Helpers ────────────────────────────────────────────────────────────────── */
const parseRequestedData = (raw: string): string => {
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>;
    const fields = Array.isArray(obj.fields) ? (obj.fields as string[]).join(', ') : '—';
    const retention = typeof obj.retention === 'string' ? obj.retention : '—';
    return `Campos: ${fields} · Retención: ${retention}`;
  } catch {
    return raw;
  }
};

/* ─── Componente principal ───────────────────────────────────────────────────── */
const SolicitudesPage = () => {
  const { user } = useAuth();
  const { isDpo, isJefeDominio, userDomains } = usePermissions();

  const [requests, setRequests] = useState<PurposeRequest[]>([...MOCK_REQUESTS]);

  // Filtro de estado (solo DPO)
  const [statusFilter, setStatusFilter] = useState<RequestStatus | 'ALL'>('ALL');

  // Modal crear solicitud (JEFE_DOMINIO)
  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState({ title: '', justification: '', domainId: '', fields: '', retention: '' });
  const [createError, setCreateError] = useState<string | null>(null);

  // Modal revisar solicitud (DPO)
  const [reviewing, setReviewing] = useState<PurposeRequest | null>(null);
  const [reviewStatus, setReviewStatus] = useState<'APPROVED' | 'REJECTED'>('APPROVED');
  const [reviewNotes, setReviewNotes] = useState('');
  const [reviewError, setReviewError] = useState<string | null>(null);

  // Modal ver detalle
  const [detail, setDetail] = useState<PurposeRequest | null>(null);

  /* ── Filtrado de lista ───────────────────────────────────────────────────── */
  const filtered = useMemo(() => {
    let list = requests;
    // JEFE_DOMINIO: solo ve sus propias solicitudes
    if (isJefeDominio && user) {
      list = list.filter((r) => r.requesterId === user.id);
    }
    // DPO: puede filtrar por estado
    if (isDpo && statusFilter !== 'ALL') {
      list = list.filter((r) => r.status === statusFilter);
    }
    return [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [requests, statusFilter, isDpo, isJefeDominio, user]);

  /* ── Crear solicitud ─────────────────────────────────────────────────────── */
  const handleCreate = () => {
    const { title, justification, domainId, fields, retention } = createForm;
    if (!title.trim())        { setCreateError('El título es obligatorio.');        return; }
    if (!justification.trim()){ setCreateError('La justificación es obligatoria.');  return; }
    if (!domainId)            { setCreateError('Debes seleccionar un dominio.');     return; }
    if (!fields.trim())       { setCreateError('Indica los campos de datos.');       return; }
    if (!retention.trim())    { setCreateError('Indica el período de retención.');   return; }

    const domain = MOCK_DOMAINS.find((d) => d.id === domainId);
    if (!domain) { setCreateError('Dominio inválido.'); return; }

    const newReq: PurposeRequest = {
      id:            `req-${Date.now()}`,
      title:         title.trim(),
      justification: justification.trim(),
      // requestedData es un JSON string (regla de negocio #5)
      requestedData: JSON.stringify({
        fields:    fields.split(',').map((f) => f.trim()).filter(Boolean),
        retention: retention.trim(),
      }),
      domainId:      domain.id,
      domainName:    domain.name,
      requesterId:   user?.id ?? '',
      requesterName: user?.name ?? '',
      status:        'PENDING',
      reviewerId:    null,
      reviewerName:  null,
      reviewNotes:   null,
      createdAt:     new Date().toISOString(),
      updatedAt:     null,
    };

    addMockRequest(newReq);
    setRequests((prev) => [newReq, ...prev]);
    setCreateForm({ title: '', justification: '', domainId: '', fields: '', retention: '' });
    setCreateError(null);
    setShowCreate(false);
  };

  const handleCloseCreate = () => {
    setShowCreate(false);
    setCreateForm({ title: '', justification: '', domainId: '', fields: '', retention: '' });
    setCreateError(null);
  };

  /* ── Revisar solicitud (DPO) ─────────────────────────────────────────────── */
  const handleReview = () => {
    if (!reviewing || !user) return;
    // Si rechaza, las notas son obligatorias (Ley 21.719 trazabilidad)
    if (reviewStatus === 'REJECTED' && !reviewNotes.trim()) {
      setReviewError('Las notas de revisión son obligatorias al rechazar una solicitud (Ley 21.719).');
      return;
    }
    reviewMockRequest(
      reviewing.id,
      reviewStatus,
      user.id,
      user.name,
      reviewNotes.trim() || null,
    );
    setRequests((prev) =>
      prev.map((r) =>
        r.id === reviewing.id
          ? { ...r, status: reviewStatus, reviewerId: user.id, reviewerName: user.name, reviewNotes: reviewNotes.trim() || null, updatedAt: new Date().toISOString() }
          : r,
      ),
    );
    setReviewing(null);
    setReviewNotes('');
    setReviewStatus('APPROVED');
    setReviewError(null);
  };

  const handleCloseReview = () => {
    setReviewing(null);
    setReviewNotes('');
    setReviewStatus('APPROVED');
    setReviewError(null);
  };

  /* ── Dominios disponibles para el JEFE_DOMINIO ───────────────────────────── */
  const availableDomains = useMemo(
    () => MOCK_DOMAINS.filter((d) => d.active && userDomains.includes(d.name)),
    [userDomains],
  );

  /* ── Contadores para DPO ─────────────────────────────────────────────────── */
  const pendingCount = requests.filter((r) => r.status === 'PENDING').length;

  return (
    <div className={styles.page}>
      {/* ── Encabezado ───────────────────────────────────────────────────── */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>
            {isDpo ? 'Solicitudes de Propósito' : 'Mis Solicitudes'}
          </h2>
          <p className={styles.subtitle}>
            {isDpo
              ? 'Revisa y aprueba o rechaza las solicitudes de tratamiento de datos enviadas por los jefes de dominio.'
              : 'Gestiona tus solicitudes de propósito de tratamiento de datos para los dominios asignados.'}
          </p>
        </div>
        {isJefeDominio && (
          <Button variant="primary" size="sm" onClick={() => setShowCreate(true)}>
            + Nueva solicitud
          </Button>
        )}
      </div>

      {/* ── Filtros de estado (DPO) ───────────────────────────────────────── */}
      {isDpo && (
        <div className={styles.filterRow}>
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((s) => {
            const count = s === 'ALL'
              ? requests.length
              : requests.filter((r) => r.status === s).length;
            return (
              <button
                key={s}
                className={[styles.filterChip, statusFilter === s ? styles.filterChipActive : ''].join(' ')}
                onClick={() => setStatusFilter(s)}
              >
                {s === 'ALL' ? 'Todas' : STATUS_LABEL[s]}
                <span className={styles.filterCount}>{count}</span>
              </button>
            );
          })}
          {pendingCount > 0 && statusFilter !== 'PENDING' && (
            <span className={styles.pendingAlert}>
              ⚠ {pendingCount} pendiente{pendingCount !== 1 ? 's' : ''} de revisión
            </span>
          )}
        </div>
      )}

      {/* ── Tabla de solicitudes ──────────────────────────────────────────── */}
      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {filtered.length === 0 ? (
            <div className={styles.empty}>
              <span className={styles.emptyIcon}>📋</span>
              <p>
                {isJefeDominio
                  ? 'Aún no has enviado solicitudes. Crea una para comenzar.'
                  : 'No hay solicitudes con los filtros aplicados.'}
              </p>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Título</th>
                  <th>Dominio</th>
                  {isDpo && <th>Solicitante</th>}
                  <th>Estado</th>
                  <th>Fecha</th>
                  <th>Acción</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((req) => (
                  <tr key={req.id} className={req.status === 'REJECTED' ? styles.rowRejected : ''}>
                    <td>
                      <button className={styles.titleLink} onClick={() => setDetail(req)}>
                        {req.title}
                      </button>
                      {/* Notas de rechazo visibles directamente (regla de negocio #4) */}
                      {req.status === 'REJECTED' && req.reviewNotes && (
                        <p className={styles.rejectedNote}>
                          <strong>Motivo:</strong> {req.reviewNotes}
                        </p>
                      )}
                    </td>
                    <td className={styles.cellDomain}>{req.domainName}</td>
                    {isDpo && <td className={styles.cellRequester}>{req.requesterName}</td>}
                    <td><StatusBadge status={req.status} /></td>
                    <td className={styles.cellDate}>{formatDate(req.createdAt)}</td>
                    <td>
                      {isDpo && req.status === 'PENDING' ? (
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => { setReviewing(req); setReviewStatus('APPROVED'); }}
                        >
                          Revisar
                        </Button>
                      ) : (
                        <button className={styles.detailLink} onClick={() => setDetail(req)}>
                          Ver detalle
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      {/* ── Modal: Crear solicitud ────────────────────────────────────────── */}
      <Modal open={showCreate} onClose={handleCloseCreate} variant="center">
        <div className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>Nueva solicitud de propósito</h3>
            <button className={styles.closeBtn} onClick={handleCloseCreate}>✕</button>
          </div>
          <p className={styles.modalHint}>
            Describe el propósito de tratamiento de datos y los campos requeridos. El DPO revisará tu solicitud.
          </p>

          <div className={styles.fields}>
            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="sr-title">Título <span className={styles.req}>*</span></label>
              <input id="sr-title" type="text" className={styles.input}
                placeholder="Ej: Campaña email marketing Q2"
                value={createForm.title}
                onChange={(e) => { setCreateForm((f) => ({ ...f, title: e.target.value })); setCreateError(null); }}
              />
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="sr-domain">Dominio <span className={styles.req}>*</span></label>
              {availableDomains.length === 0 ? (
                <p className={styles.noDomainNote}>No tienes dominios activos asignados.</p>
              ) : (
                <select id="sr-domain" className={styles.select}
                  value={createForm.domainId}
                  onChange={(e) => { setCreateForm((f) => ({ ...f, domainId: e.target.value })); setCreateError(null); }}
                >
                  <option value="">Selecciona un dominio...</option>
                  {availableDomains.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              )}
            </div>

            <div className={styles.fieldGroup}>
              <label className={styles.fieldLabel} htmlFor="sr-just">Justificación <span className={styles.req}>*</span></label>
              <textarea id="sr-just" className={styles.textarea} rows={3}
                placeholder="Explica por qué se necesitan estos datos y cómo se utilizarán..."
                value={createForm.justification}
                onChange={(e) => { setCreateForm((f) => ({ ...f, justification: e.target.value })); setCreateError(null); }}
              />
            </div>

            <div className={styles.fieldRow}>
              <div className={styles.fieldGroup}>
                <label className={styles.fieldLabel} htmlFor="sr-fields">Campos de datos <span className={styles.req}>*</span></label>
                <input id="sr-fields" type="text" className={styles.input}
                  placeholder="email, nombre, teléfono"
                  value={createForm.fields}
                  onChange={(e) => { setCreateForm((f) => ({ ...f, fields: e.target.value })); setCreateError(null); }}
                />
                <span className={styles.fieldHint}>Separados por coma</span>
              </div>
              <div className={styles.fieldGroup}>
                <label className={styles.fieldLabel} htmlFor="sr-ret">Retención <span className={styles.req}>*</span></label>
                <input id="sr-ret" type="text" className={styles.input}
                  placeholder="Ej: 6 meses"
                  value={createForm.retention}
                  onChange={(e) => { setCreateForm((f) => ({ ...f, retention: e.target.value })); setCreateError(null); }}
                />
              </div>
            </div>

            {createError && <p className={styles.formError}>{createError}</p>}
          </div>

          <div className={styles.modalFooter}>
            <Button variant="ghost" onClick={handleCloseCreate}>Cancelar</Button>
            <Button variant="primary" onClick={handleCreate}
              disabled={!createForm.title.trim() || !createForm.domainId || !createForm.justification.trim()}
            >
              Enviar al DPO
            </Button>
          </div>
        </div>
      </Modal>

      {/* ── Modal: Revisar solicitud (DPO) ────────────────────────────────── */}
      <Modal open={!!reviewing} onClose={handleCloseReview} variant="center">
        {reviewing && (
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Revisar solicitud</h3>
              <button className={styles.closeBtn} onClick={handleCloseReview}>✕</button>
            </div>

            <div className={styles.reviewInfo}>
              <p className={styles.reviewTitle}>{reviewing.title}</p>
              <p className={styles.reviewMeta}>
                <strong>Dominio:</strong> {reviewing.domainName} ·{' '}
                <strong>Solicitante:</strong> {reviewing.requesterName}
              </p>
              <p className={styles.reviewJust}>{reviewing.justification}</p>
              <p className={styles.reviewData}>{parseRequestedData(reviewing.requestedData)}</p>
            </div>

            <div className={styles.fields}>
              <div className={styles.fieldGroup}>
                <label className={styles.fieldLabel}>Decisión</label>
                <div className={styles.decisionRow}>
                  <button
                    className={[styles.decisionBtn, reviewStatus === 'APPROVED' ? styles.decisionApproved : ''].join(' ')}
                    onClick={() => setReviewStatus('APPROVED')}
                  >
                    ✓ Aprobar
                  </button>
                  <button
                    className={[styles.decisionBtn, reviewStatus === 'REJECTED' ? styles.decisionRejected : ''].join(' ')}
                    onClick={() => setReviewStatus('REJECTED')}
                  >
                    ✕ Rechazar
                  </button>
                </div>
              </div>

              <div className={styles.fieldGroup}>
                <label className={styles.fieldLabel} htmlFor="rv-notes">
                  Notas de revisión
                  {reviewStatus === 'REJECTED' && <span className={styles.req}> * obligatorio</span>}
                </label>
                <textarea id="rv-notes" className={styles.textarea} rows={3}
                  placeholder={reviewStatus === 'REJECTED'
                    ? 'Indica el motivo del rechazo (Ley 21.719 — obligatorio)...'
                    : 'Notas opcionales...'}
                  value={reviewNotes}
                  onChange={(e) => { setReviewNotes(e.target.value); setReviewError(null); }}
                />
              </div>

              {reviewError && <p className={styles.formError}>{reviewError}</p>}
            </div>

            <div className={styles.modalFooter}>
              <Button variant="ghost" onClick={handleCloseReview}>Cancelar</Button>
              <Button
                variant={reviewStatus === 'APPROVED' ? 'primary' : 'danger'}
                onClick={handleReview}
              >
                {reviewStatus === 'APPROVED' ? 'Confirmar aprobación' : 'Confirmar rechazo'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* ── Modal: Detalle de solicitud ───────────────────────────────────── */}
      <Modal open={!!detail} onClose={() => setDetail(null)} variant="drawer">
        {detail && (
          <div className={styles.detailPanel}>
            <div className={styles.detailHeader}>
              <div>
                <StatusBadge status={detail.status} />
                <h3 className={styles.detailTitle}>{detail.title}</h3>
              </div>
              <button className={styles.closeBtn} onClick={() => setDetail(null)}>✕</button>
            </div>

            <div className={styles.detailBody}>
              <dl className={styles.detailList}>
                <dt>Dominio</dt>
                <dd>{detail.domainName}</dd>

                <dt>Solicitante</dt>
                <dd>{detail.requesterName}</dd>

                <dt>Fecha de envío</dt>
                <dd>{formatDate(detail.createdAt)}</dd>

                <dt>Justificación</dt>
                <dd>{detail.justification}</dd>

                <dt>Datos solicitados</dt>
                <dd>{parseRequestedData(detail.requestedData)}</dd>

                {detail.reviewerName && (
                  <>
                    <dt>Revisado por</dt>
                    <dd>{detail.reviewerName}</dd>
                  </>
                )}

                {detail.updatedAt && (
                  <>
                    <dt>Fecha de revisión</dt>
                    <dd>{formatDate(detail.updatedAt)}</dd>
                  </>
                )}

                {detail.reviewNotes && (
                  <>
                    <dt>Notas del DPO</dt>
                    <dd className={detail.status === 'REJECTED' ? styles.rejectedNoteDetail : ''}>
                      {detail.reviewNotes}
                    </dd>
                  </>
                )}
              </dl>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default SolicitudesPage;
