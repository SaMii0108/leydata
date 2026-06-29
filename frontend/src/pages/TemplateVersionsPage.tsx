import { useNavigate, useParams } from 'react-router-dom';
import { getTemplate } from '../utils/mockData';
import styles from './TemplateVersionsPage.module.css';

const TemplateVersionsPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const tpl = id ? getTemplate(id) : undefined;

  if (!tpl) {
    return (
      <div className={styles.page}>
        <button className={styles.backLink} onClick={() => navigate('/plantillas')}>← Volver</button>
        <p className={styles.notFound}>Plantilla no encontrada.</p>
      </div>
    );
  }

  const sorted = [...tpl.versiones].sort((a, b) => b.version - a.version);

  return (
    <div className={styles.page}>
      <button className={styles.backLink} onClick={() => navigate('/plantillas')}>
        ← Volver a Plantillas
      </button>

      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Historial de Versiones</h2>
          <p className={styles.subtitle}>
            <strong>{tpl.nombre}</strong> · Versión actual: v{tpl.version}
          </p>
        </div>
      </div>

      <div className={styles.timeline}>
        {sorted.map((v, idx) => (
          <div key={v.version} className={styles.timelineItem}>
            <div className={styles.timelineLeft}>
              <div className={[styles.dot, idx === 0 ? styles.dotCurrent : ''].join(' ')} />
              {idx < sorted.length - 1 && <div className={styles.line} />}
            </div>
            <div className={styles.card}>
              <div className={styles.cardHeader}>
                <span className={styles.versionBadge}>
                  v{v.version}
                  {idx === 0 && <span className={styles.currentTag}>Actual</span>}
                </span>
                <span className={styles.versionDate}>{v.fecha}</span>
              </div>
              <p className={styles.versionNota}>{v.nota}</p>
              <p className={styles.versionAutor}>Modificado por <strong>{v.autor}</strong></p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default TemplateVersionsPage;
