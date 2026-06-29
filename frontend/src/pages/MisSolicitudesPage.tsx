import { useState, useEffect } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import { getMyPurposeRequests, ApiError } from '../api/purposeRequestsApi';
import type { PurposeRequestSummary } from '../api/purposeRequestsApi';
import styles from './MisSolicitudesPage.module.css';

const STATUS_LABEL: Record<PurposeRequestSummary['status'], string> = {
  PENDING:  'Pendiente',
  APPROVED: 'Aprobada',
  REJECTED: 'Rechazada',
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

const MisSolicitudesPage = () => {
  const { accessToken } = useAuth();
  const [requests, setRequests] = useState<PurposeRequestSummary[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getMyPurposeRequests(accessToken)
      .then((data) => { if (!cancelled) setRequests(data); })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof ApiError ? err.message : 'No se pudieron cargar las solicitudes');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Mis Solicitudes de Finalidad</h2>
          <p className={styles.subtitle}>Solicitudes de tratamiento de datos enviadas al DPO</p>
        </div>
      </div>

      {error && (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>
          {error}
        </p>
      )}

      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {loading ? (
            <p className={styles.stateMsg}>Cargando solicitudes…</p>
          ) : requests.length === 0 ? (
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="12" y1="18" x2="12" y2="12"/>
                  <line x1="9" y1="15" x2="15" y2="15"/>
                </svg>
              </div>
              <p className={styles.emptyTitle}>Sin solicitudes registradas</p>
              <p className={styles.emptyHint}>Aún no has enviado ninguna solicitud de finalidad al DPO.</p>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Título</th>
                  <th>Dominio</th>
                  <th>Estado</th>
                  <th>Solicitada</th>
                  <th>Revisada</th>
                  <th>Revisor</th>
                  <th>Motivo de rechazo</th>
                </tr>
              </thead>
              <tbody>
                {requests.map((req) => (
                  <tr key={req.id}>
                    <td className={styles.cellTitle}>{req.title}</td>
                    <td className={styles.cellDomain}>{req.domainName}</td>
                    <td>
                      <span className={[styles.statusBadge, styles[`status_${req.status.toLowerCase()}`]].join(' ')}>
                        {STATUS_LABEL[req.status]}
                      </span>
                    </td>
                    <td className={styles.cellDate}>{formatDate(req.createdAt)}</td>
                    <td className={styles.cellDate}>
                      {req.updatedAt && req.status !== 'PENDING'
                        ? formatDate(req.updatedAt)
                        : <span className={styles.noValue}>—</span>}
                    </td>
                    <td className={styles.cellReviewer}>
                      {req.reviewerName ?? <span className={styles.noValue}>—</span>}
                    </td>
                    <td className={styles.cellNotes}>
                      {req.status === 'REJECTED' && req.reviewNotes
                        ? req.reviewNotes
                        : <span className={styles.noValue}>—</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </div>
  );
};

export default MisSolicitudesPage;
