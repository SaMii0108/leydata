import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import {
  getTemplate,
  getTemplatePurposes,
  ApiError,
  type TemplateResponse,
  type TemplatePurposeResponse,
} from '../api/templatesApi';
import styles from './TemplatePreviewPage.module.css';

const STATUS_LABEL: Record<string, string> = {
  DRAFT:    'Borrador',
  APPROVED: 'Aprobada',
  ACTIVE:   'Activa',
};

const TemplatePreviewPage = () => {
  const { id } = useParams<{ id: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const [template, setTemplate] = useState<TemplateResponse | null>(null);
  const [purposes, setPurposes] = useState<TemplatePurposeResponse[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [tpl, purps] = await Promise.all([
          getTemplate(id, accessToken),
          getTemplatePurposes(id, accessToken),
        ]);
        if (cancelled) return;
        setTemplate(tpl);
        setPurposes(purps);
      } catch (err) {
        if (!cancelled)
          setError(err instanceof ApiError ? err.message : 'Error al cargar la plantilla.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [id, accessToken]);

  if (loading) {
    return <div className={styles.wrapper}><p className={styles.stateMsg}>Cargando vista previa…</p></div>;
  }

  if (error || !template) {
    return (
      <div className={styles.wrapper}>
        <div className={styles.notFound}>
          <p>{error ?? 'Plantilla no encontrada.'}</p>
          <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
            Volver a Plantillas
          </button>
        </div>
      </div>
    );
  }

  const sorted          = [...purposes].sort((a, b) => a.orderPosition - b.orderPosition);
  const visiblePurposes = sorted.filter((p) =>  p.isVisible);
  const hiddenPurposes  = sorted.filter((p) => !p.isVisible);

  return (
    <div className={styles.wrapper}>
      {/* Barra superior */}
      <div className={styles.topBar}>
        <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
          ← Volver a Plantillas
        </button>
        <div className={styles.topMeta}>
          <span className={styles.previewLabel}>Vista previa</span>
          <span className={styles.templateName}>{template.name}</span>
          <span className={styles.versionTag}>v{template.version}</span>
        </div>
        <button
          className={styles.editBtn}
          onClick={() => navigate(`/plantillas/${template.id}/editar`)}
        >
          Editar plantilla
        </button>
      </div>

      {/* Contenido */}
      <div className={styles.content}>
        {/* Banner de estado (solo si no está activa) */}
        {template.status !== 'ACTIVE' && (
          <div className={[styles.statusBanner, styles[`banner_${template.status.toLowerCase()}`]].join(' ')}>
            Esta plantilla está en estado <strong>{STATUS_LABEL[template.status]}</strong>.
            {template.status === 'DRAFT'    && ' Debe ser aprobada y activada antes de estar disponible para los titulares.'}
            {template.status === 'APPROVED' && ' Debe ser activada para estar disponible para los titulares.'}
          </div>
        )}

        {/* Tarjeta de vista previa */}
        <div className={styles.previewCard}>
          {/* Encabezado del formulario de consentimiento */}
          <div className={styles.previewHeader}>
            <h2 className={styles.previewTitle}>
              {template.title || template.name}
            </h2>
            {template.description && (
              <p className={styles.previewDesc}>{template.description}</p>
            )}
          </div>

          {/* Finalidades visibles */}
          <div className={styles.purposeSection}>
            <p className={styles.purposeSectionTitle}>Finalidades de tratamiento de datos</p>
            {visiblePurposes.length === 0 ? (
              <p className={styles.emptyPurposes}>
                Esta plantilla no tiene finalidades visibles asociadas.
                {template.status === 'DRAFT' && ' Agrégalas desde "Editar plantilla" antes de aprobarla.'}
              </p>
            ) : (
              <ul className={styles.purposeList}>
                {visiblePurposes.map((p) => (
                  <li key={p.purposeId} className={styles.purposeItem}>
                    <span className={styles.checkbox} aria-hidden="true">☐</span>
                    <span className={styles.purposeName}>{p.purposeName}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Finalidades ocultas — solo informativo para el DPO */}
          {hiddenPurposes.length > 0 && (
            <div className={styles.hiddenSection}>
              <p className={styles.hiddenTitle}>
                No visible para el titular ({hiddenPurposes.length} finalidad{hiddenPurposes.length !== 1 ? 'es' : ''} oculta{hiddenPurposes.length !== 1 ? 's' : ''})
              </p>
              <ul className={styles.hiddenList}>
                {hiddenPurposes.map((p) => (
                  <li key={p.purposeId} className={styles.hiddenItem}>{p.purposeName}</li>
                ))}
              </ul>
            </div>
          )}
        </div>

        <p className={styles.previewNote}>
          Vista previa basada en datos reales del backend
          {' · '}
          Los elementos de personalización visual se configurarán en versiones futuras
        </p>
      </div>
    </div>
  );
};

export default TemplatePreviewPage;
