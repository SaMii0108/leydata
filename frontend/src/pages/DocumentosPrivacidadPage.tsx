import { useState } from 'react';
import { DOCUMENTOS_PRIVACIDAD, type DocumentoEstado } from '../utils/mockData';
import styles from './DocumentosPrivacidadPage.module.css';

type Filtro = 'todos' | DocumentoEstado;

const ESTADO_LABEL: Record<DocumentoEstado, string> = {
  vigente:  'Vigente',
  borrador: 'Borrador',
  obsoleto: 'Obsoleto',
};

const DocumentosPrivacidadPage = () => {
  const [filtro, setFiltro] = useState<Filtro>('todos');

  const lista = filtro === 'todos'
    ? DOCUMENTOS_PRIVACIDAD
    : DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === filtro);

  const counts = {
    todos:    DOCUMENTOS_PRIVACIDAD.length,
    vigente:  DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === 'vigente').length,
    borrador: DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === 'borrador').length,
    obsoleto: DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === 'obsoleto').length,
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Documentos de Privacidad</h2>
          <p className={styles.subtitle}>
            Repositorio de documentos legales que respaldan las finalidades de tratamiento de datos conforme a la Ley 21.719.
          </p>
        </div>
      </div>

      {/* Filtros */}
      <div className={styles.filters}>
        {(['todos', 'vigente', 'borrador', 'obsoleto'] as Filtro[]).map((f) => (
          <button
            key={f}
            className={[styles.filterBtn, filtro === f ? styles.filterActive : ''].join(' ')}
            onClick={() => setFiltro(f)}
          >
            {f === 'todos' ? 'Todos' : ESTADO_LABEL[f as DocumentoEstado]}
            <span className={styles.filterCount}>{counts[f as keyof typeof counts]}</span>
          </button>
        ))}
      </div>

      {/* Lista */}
      {lista.length === 0 ? (
        <div className={styles.empty}>
          <p>No hay documentos en esta categoría.</p>
        </div>
      ) : (
        <div className={styles.grid}>
          {lista.map((doc) => (
            <div key={doc.id} className={styles.card}>
              <div className={styles.cardTop}>
                <span className={[styles.badge, styles[`badge_${doc.estado}`]].join(' ')}>
                  {ESTADO_LABEL[doc.estado]}
                </span>
                <span className={styles.version}>v{doc.version}</span>
              </div>
              <h3 className={styles.cardTitle}>{doc.nombre}</h3>
              <p className={styles.cardDesc}>{doc.descripcion}</p>
              <div className={styles.cardFooter}>
                <span className={styles.vigencia}>
                  Vigente hasta: <strong>{doc.fechaVigencia}</strong>
                </span>
                <span className={styles.docId}>{doc.id}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default DocumentosPrivacidadPage;
