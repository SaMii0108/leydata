import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import {
  getAllPurposeRequests,
  reviewPurposeRequest,
  ApiError,
} from '../api/purposeRequestsApi';
import type { PurposeRequestSummary } from '../api/purposeRequestsApi';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import styles from './AprobacionSolicitudesPage.module.css';

type ReviewDecision = 'APPROVED' | 'REJECTED';

const STATUS_LABEL: Record<PurposeRequestSummary['status'], string> = {
  PENDING:  'Pendiente',
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada',
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

const AprobacionSolicitudesPage = () => {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [requests, setRequests]     = useState<PurposeRequestSummary[]>([]);
  const [loading, setLoading]       = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [selected, setSelected]     = useState<PurposeRequestSummary | null>(null);
  const [decision, setDecision]     = useState<ReviewDecision | null>(null);
  const [notes, setNotes]           = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const load = useCallback(() => {
    let cancelled = false;
    setLoading(true);
    setFetchError(null);
    getAllPurposeRequests(accessToken)
      .then((data) => { if (!cancelled) setRequests(data); })
      .catch((err) => {
        if (!cancelled)
          setFetchError(err instanceof ApiError ? err.message : 'No se pudieron cargar las solicitudes');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  useEffect(load, [load]);

  useEffect(() => {
    const state = location.state as { purposeCreated?: boolean } | null;
    if (state?.purposeCreated) {
      setSuccessMsg('Finalidad creada correctamente.');
      const id = setTimeout(() => setSuccessMsg(null), 4000);
      window.history.replaceState({}, '');
      return () => clearTimeout(id);
    }
  }, [location.state]);

  const openModal = (req: PurposeRequestSummary) => {
    setSelected(req);
    setDecision(null);
    setNotes('');
    setSubmitError(null);
  };

  const closeModal = () => {
    if (submitting) return;
    setSelected(null);
    setDecision(null);
    setNotes('');
    setSubmitError(null);
  };

  const handleReview = async () => {
    if (!selected || !decision) return;
    if (decision === 'REJECTED' && !notes.trim()) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await reviewPurposeRequest(
        selected.id,
        { status: decision, reviewNotes: decision === 'REJECTED' ? notes.trim() : undefined },
        accessToken,
      );
      setRequests((prev) => prev.map((r) => r.id === updated.id ? updated : r));
      const msg = decision === 'APPROVED'
        ? `Solicitud "${updated.title}" aprobada correctamente.`
        : `Solicitud "${updated.title}" rechazada.`;
      setSuccessMsg(msg);
      setTimeout(() => setSuccessMsg(null), 4000);
      closeModal();
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Error al procesar la revisión');
    } finally {
      setSubmitting(false);
    }
  };

  const canSubmit =
    decision !== null &&
    !(decision === 'REJECTED' && !notes.trim()) &&
    !submitting;

  const parsedRequestDatos = (() => {
    if (!selected?.requestedData) return [];
    try {
      const p = JSON.parse(selected.requestedData);
      return Array.isArray(p)
        ? (p as Array<{ nombre: string; tipo: string; obligatorio: boolean }>)
        : [];
    } catch {
      return [];
    }
  })();

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Aprobación de Solicitudes</h2>
          <p className={styles.subtitle}>Revisa y decide sobre las solicitudes de finalidad enviadas por los responsables de área</p>
        </div>
      </div>

      {fetchError && (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
          {fetchError}
        </p>
      )}

      {successMsg && (
        <div className={styles.successBanner}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="9 12 11 14 15 10"/>
          </svg>
          {successMsg}
        </div>
      )}

      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {loading ? (
            <p className={styles.stateMsg}>Cargando solicitudes…</p>
          ) : requests.length === 0 ? (
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M9 11l3 3L22 4"/>
                  <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                </svg>
              </div>
              <p className={styles.emptyTitle}>Sin solicitudes registradas</p>
              <p className={styles.emptyHint}>Cuando los responsables de área envíen solicitudes, aparecerán aquí.</p>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Título</th>
                  <th>Solicitante</th>
                  <th>Dominio</th>
                  <th>Estado</th>
                  <th>Solicitada</th>
                  <th>Justificación</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {requests.map((req) => (
                  <tr key={req.id}>
                    <td className={styles.cellTitle}>{req.title}</td>
                    <td className={styles.cellSecondary}>{req.requesterName}</td>
                    <td className={styles.cellSecondary}>{req.domainName}</td>
                    <td>
                      <span className={[styles.statusBadge, styles[`status_${req.status.toLowerCase()}`]].join(' ')}>
                        {STATUS_LABEL[req.status]}
                      </span>
                    </td>
                    <td className={styles.cellDate}>{formatDate(req.createdAt)}</td>
                    <td className={styles.cellJustification}>{req.justification}</td>
                    <td className={styles.cellAction}>
                      {req.status === 'PENDING' && (
                        <Button variant="secondary" size="sm" onClick={() => openModal(req)}>
                          Revisar
                        </Button>
                      )}
                      {req.status === 'APPROVED' && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => navigate('/finalidades/nueva', { state: { request: req } })}
                        >
                          Crear Finalidad
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <Modal open={selected !== null} onClose={closeModal} variant="center">
        {selected && (
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Revisar solicitud</h3>
              <button className={styles.closeBtn} onClick={closeModal} disabled={submitting}>✕</button>
            </div>

            <div className={styles.modalMeta}>
              <p className={styles.modalReqTitle}>{selected.title}</p>
              <p className={styles.modalReqSub}>
                {selected.requesterName} · {selected.domainName} · {formatDate(selected.createdAt)}
              </p>
            </div>

            <div className={styles.modalSection}>
              <p className={styles.modalLabel}>Justificación</p>
              <p className={styles.modalJustification}>{selected.justification}</p>
            </div>

            {parsedRequestDatos.length > 0 && (
              <div className={styles.modalSection}>
                <p className={styles.modalLabel}>Datos declarados</p>
                <div className={styles.modalDatosList}>
                  {parsedRequestDatos.map((d, i) => (
                    <div key={i} className={styles.modalDatoItem}>
                      <span className={styles.modalDatoNombre}>{d.nombre}</span>
                      <span className={styles.modalDatoTipo}>{d.tipo}</span>
                      <span className={d.obligatorio ? styles.modalDatoOblig : styles.modalDatoOpcional}>
                        {d.obligatorio ? 'Obligatorio' : 'Opcional'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className={styles.modalSection}>
              <p className={styles.modalLabel}>Decisión</p>
              <div className={styles.decisionRow}>
                <button
                  className={[styles.decisionBtn, decision === 'APPROVED' ? styles.decisionApprove : ''].join(' ')}
                  onClick={() => setDecision('APPROVED')}
                  disabled={submitting}
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  Aprobar
                </button>
                <button
                  className={[styles.decisionBtn, decision === 'REJECTED' ? styles.decisionReject : ''].join(' ')}
                  onClick={() => setDecision('REJECTED')}
                  disabled={submitting}
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                  Rechazar
                </button>
              </div>
            </div>

            {decision === 'REJECTED' && (
              <div className={styles.modalSection}>
                <label className={styles.modalLabel} htmlFor="review-notes">
                  Motivo del rechazo <span className={styles.required}>*</span>
                </label>
                <p className={styles.modalHint}>Obligatorio por Ley 21.719 — el solicitante recibirá esta explicación.</p>
                <textarea
                  id="review-notes"
                  className={styles.textarea}
                  rows={4}
                  placeholder="Describe el motivo por el que se rechaza esta solicitud…"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  disabled={submitting}
                />
              </div>
            )}

            {submitError && (
              <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
                {submitError}
              </p>
            )}

            <div className={styles.modalFooter}>
              <Button variant="ghost" onClick={closeModal} disabled={submitting}>Cancelar</Button>
              <Button
                variant={decision === 'REJECTED' ? 'danger' : 'primary'}
                onClick={handleReview}
                disabled={!canSubmit}
              >
                {submitting
                  ? 'Procesando…'
                  : decision === 'APPROVED'
                    ? 'Confirmar aprobación'
                    : decision === 'REJECTED'
                      ? 'Confirmar rechazo'
                      : 'Selecciona una decisión'}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default AprobacionSolicitudesPage;
