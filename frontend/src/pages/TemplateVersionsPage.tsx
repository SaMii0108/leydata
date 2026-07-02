import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import {
  getTemplate,
  getTemplateFamily,
  ApiError,
  type TemplateResponse,
  type TemplateStatus,
} from '../api/templatesApi';
import styles from './TemplateVersionsPage.module.css';

const STATUS_LABEL: Record<TemplateStatus, string> = {
  ACTIVE:   'Activa',
  APPROVED: 'Aprobada',
  DRAFT:    'Borrador',
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

const TemplateVersionsPage = () => {
  const { id } = useParams<{ id: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const [versions, setVersions] = useState<TemplateResponse[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    (async () => {
      setLoading(true);
      setError(null);
      try {
        const tpl    = await getTemplate(id, accessToken);
        if (cancelled) return;
        const family = await getTemplateFamily(tpl.templateKey, tpl.domainId, accessToken);
        if (cancelled) return;
        setVersions(family.sort((a, b) => b.version - a.version));
      } catch (err) {
        if (!cancelled)
          setError(err instanceof ApiError ? err.message : 'Error al cargar el historial de versiones.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [id, accessToken]);

  if (loading) {
    return <div className={styles.page}><p className={styles.stateMsg}>Cargando versiones…</p></div>;
  }

  if (error || versions.length === 0) {
    return (
      <div className={styles.page}>
        <button className={styles.backLink} onClick={() => navigate('/plantillas')}>← Volver a Plantillas</button>
        <p className={styles.notFound}>{error ?? 'No se encontraron versiones para esta plantilla.'}</p>
      </div>
    );
  }

  const latest = versions[0];

  return (
    <div className={styles.page}>
      <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
        ← Volver a Plantillas
      </button>

      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Historial de Versiones</h2>
          <p className={styles.subtitle}>
            <strong>{latest.name}</strong>
            {' · '}
            <code className={styles.keyCode}>{latest.templateKey}</code>
            {' · '}
            {versions.length} versión{versions.length !== 1 ? 'es' : ''}
          </p>
        </div>
      </div>

      <div className={styles.timeline}>
        {versions.map((v, idx) => (
          <div key={v.id} className={styles.timelineItem}>
            <div className={styles.timelineLeft}>
              <div className={[
                styles.dot,
                v.status === 'ACTIVE'   ? styles.dotActive   : '',
                v.status === 'APPROVED' ? styles.dotApproved : '',
              ].join(' ')} />
              {idx < versions.length - 1 && <div className={styles.line} />}
            </div>

            <div className={styles.card}>
              <div className={styles.cardHeader}>
                <div className={styles.cardHeaderLeft}>
                  <span className={styles.versionBadge}>
                    v{v.version}
                    {idx === 0 && <span className={styles.currentTag}>Última</span>}
                  </span>
                  <span className={[styles.statusBadge, styles[`status_${v.status.toLowerCase()}`]].join(' ')}>
                    {STATUS_LABEL[v.status]}
                  </span>
                </div>
                <span className={styles.versionDate}>{formatDate(v.createdAt)}</span>
              </div>

              <p className={styles.cardName}>{v.name}</p>
              {v.description && <p className={styles.cardDesc}>{v.description}</p>}

              <div className={styles.dateMeta}>
                {v.approvedAt && (
                  <span className={styles.dateRow}>Aprobada el {formatDate(v.approvedAt)}</span>
                )}
                {v.activationDate && (
                  <span className={styles.dateRow}>Activada el {formatDate(v.activationDate)}</span>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default TemplateVersionsPage;
