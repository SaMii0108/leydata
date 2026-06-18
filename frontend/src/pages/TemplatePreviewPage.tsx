import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ConsentPreview from '../components/common/ConsentPreview';
import type { TemplateConfig } from '../components/common/ConsentPreview';
import { getTemplate } from '../utils/mockData';
import styles from './TemplatePreviewPage.module.css';

const TemplatePreviewPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tpl = id ? getTemplate(id) : undefined;
  const [result, setResult] = useState<'accepted' | 'rejected' | null>(null);
  const [checkedIds, setCheckedIds] = useState<string[]>([]);

  if (!tpl) {
    return (
      <div className={styles.wrapper}>
        <div className={styles.notFound}>
          <p>Plantilla no encontrada.</p>
          <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
            Volver a Plantillas
          </button>
        </div>
      </div>
    );
  }

  const config: TemplateConfig = {
    logo: '',
    primaryColor: tpl.primaryColor,
    titleColor: '#111827',
    subtitleColor: '#6b7280',
    buttonLabel: tpl.buttonLabel,
    buttonSize: 'md',
    buttonRadius: 'sm',
    domain: tpl.dominio,
    purpose: tpl.finalidad,
    requiredFields: [],
  };

  const handleAccept = (ids: string[]) => {
    setCheckedIds(ids);
    setResult('accepted');
  };

  const handleReject = () => setResult('rejected');
  const handleReset  = () => { setResult(null); setCheckedIds([]); };

  return (
    <div className={styles.wrapper}>
      {/* Barra superior */}
      <div className={styles.topBar}>
        <button className={styles.backLink} onClick={() => navigate(`/plantillas`)}>
          ← Volver a Plantillas
        </button>
        <div className={styles.topMeta}>
          <span className={styles.previewLabel}>Vista previa</span>
          <span className={styles.templateName}>{tpl.nombre}</span>
          <span className={styles.versionTag}>v{tpl.version}</span>
        </div>
        <button
          className={styles.editBtn}
          onClick={() => navigate(`/plantillas/${tpl.id}/editar`)}
        >
          Editar plantilla
        </button>
      </div>

      {/* Contenido centrado */}
      <div className={styles.content}>
        {result ? (
          /* Resultado de la interacción */
          <div className={[styles.resultCard, result === 'accepted' ? styles.accepted : styles.rejected].join(' ')}>
            <div className={styles.resultIcon}>
              {result === 'accepted' ? '✓' : '✕'}
            </div>
            <h3 className={styles.resultTitle}>
              {result === 'accepted' ? 'Consentimiento registrado' : 'Consentimiento rechazado'}
            </h3>
            {result === 'accepted' && checkedIds.length > 0 && (
              <div className={styles.checkedList}>
                <p className={styles.checkedLabel}>Datos autorizados:</p>
                {tpl.dataItems
                  .filter((d) => checkedIds.includes(d.id))
                  .map((d) => <span key={d.id} className={styles.checkedItem}>✓ {d.titulo}</span>)}
              </div>
            )}
            <p className={styles.resultNote}>
              {result === 'accepted'
                ? 'En el sistema real, este consentimiento quedaría registrado con timestamp, IP y hash de integridad.'
                : 'El titular ha rechazado el tratamiento de sus datos.'}
            </p>
            <button className={styles.resetBtn} onClick={handleReset}>
              Volver a probar
            </button>
          </div>
        ) : (
          <div className={styles.widgetWrap}>
            <ConsentPreview
              config={config}
              dataItems={tpl.dataItems}
              templateName={tpl.nombre}
              interactive
              showLabel={false}
              onAccept={handleAccept}
              onReject={handleReject}
            />
            <p className={styles.demoNote}>
              Vista previa interactiva — los botones funcionan para demostración
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default TemplatePreviewPage;
