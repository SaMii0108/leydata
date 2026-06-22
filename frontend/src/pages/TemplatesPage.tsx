import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/common/Button';
import { TEMPLATES, type Template, type TemplateEstado } from '../utils/mockData';
import styles from './TemplatesPage.module.css';

const ESTADO_LABEL: Record<TemplateEstado, string> = {
  activa:   'Activa',
  inactiva: 'Inactiva',
  borrador: 'Borrador',
};

const TemplateCard = ({ tpl, onEdit, onVersions, onPreview }: {
  tpl: Template;
  onEdit: () => void;
  onVersions: () => void;
  onPreview: () => void;
}) => (
  <div className={styles.card}>
    <div className={styles.cardTop}>
      <div className={styles.cardMeta}>
        <span className={[styles.badge, styles[`badge_${tpl.estado}`]].join(' ')}>
          {ESTADO_LABEL[tpl.estado]}
        </span>
        <span className={styles.cardDomain}>{tpl.dominio}</span>
        <span className={styles.cardVersion}>v{tpl.version}</span>
      </div>
      <div className={styles.colorDot} style={{ background: tpl.primaryColor }} />
    </div>

    <h3 className={styles.cardName}>{tpl.nombre}</h3>
    {tpl.descripcion && (
      <p className={styles.cardDesc}>{tpl.descripcion}</p>
    )}

    <div className={styles.cardFooter}>
      <span className={styles.cardCreated}>
        Creado por <strong>{tpl.creadoPor}</strong> · {tpl.creadoEn}
        {' · '}{tpl.dataItems.length} campo{tpl.dataItems.length !== 1 ? 's' : ''}
      </span>
      <div className={styles.cardActions}>
        <Button variant="ghost" size="sm" onClick={onEdit}>Editar</Button>
        <Button variant="ghost" size="sm" onClick={onVersions}>Versiones</Button>
        <Button variant="secondary" size="sm" onClick={onPreview}>Vista previa</Button>
      </div>
    </div>
  </div>
);

const TemplatesPage = () => {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<TemplateEstado | 'todas'>('todas');

  const visible = filter === 'todas'
    ? TEMPLATES
    : TEMPLATES.filter((t) => t.estado === filter);

  return (
    <div className={styles.page}>
      {/* Encabezado */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Plantillas</h2>
          <p className={styles.subtitle}>
            Gestiona las plantillas de consentimiento del módulo · Ley 21.719
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button variant="ghost" size="sm" onClick={() => navigate('/mock/register')}>
            Demo de registro
          </Button>
          <Button variant="primary" size="sm" onClick={() => navigate('/plantillas/nueva')}>
            + Crear Plantilla
          </Button>
        </div>
      </div>

      {/* Filtros */}
      <div className={styles.filters}>
        {(['todas', 'activa', 'borrador', 'inactiva'] as const).map((f) => (
          <button
            key={f}
            className={[styles.filterBtn, filter === f ? styles.filterActive : ''].join(' ')}
            onClick={() => setFilter(f)}
          >
            {f === 'todas' ? 'Todas' : ESTADO_LABEL[f]}
            {f === 'todas' && <span className={styles.filterCount}>{TEMPLATES.length}</span>}
            {f !== 'todas' && (
              <span className={styles.filterCount}>
                {TEMPLATES.filter((t) => t.estado === f).length}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Lista */}
      {visible.length === 0 ? (
        <div className={styles.empty}>
          <p className={styles.emptyText}>No hay plantillas con el filtro seleccionado.</p>
        </div>
      ) : (
        <div className={styles.list}>
          {visible.map((tpl) => (
            <TemplateCard
              key={tpl.id}
              tpl={tpl}
              onEdit={() => navigate(`/plantillas/${tpl.id}/editar`)}
              onVersions={() => navigate(`/plantillas/${tpl.id}/versiones`)}
              onPreview={() => navigate(`/preview/template/${tpl.id}`)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default TemplatesPage;
