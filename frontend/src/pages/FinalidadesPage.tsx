import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { SOLICITUDES, type SolicitudEstado } from '../utils/mockData';
import styles from './FinalidadesPage.module.css';

type Filtro = 'todas' | SolicitudEstado;

const ESTADO_LABEL: Record<SolicitudEstado, string> = {
  pendiente: 'Pendiente',
  aprobada:  'Aprobada',
  rechazada: 'Rechazada',
};

const FinalidadesPage = () => {
  const navigate = useNavigate();
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [expanded, setExpanded] = useState<string | null>(null);

  const lista = filtro === 'todas' ? SOLICITUDES : SOLICITUDES.filter((s) => s.estado === filtro);

  const counts = {
    todas:     SOLICITUDES.length,
    pendiente: SOLICITUDES.filter((s) => s.estado === 'pendiente').length,
    aprobada:  SOLICITUDES.filter((s) => s.estado === 'aprobada').length,
    rechazada: SOLICITUDES.filter((s) => s.estado === 'rechazada').length,
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Solicitudes de Finalidad</h2>
          <p className={styles.subtitle}>
            Gestiona las solicitudes de finalidad de tratamiento de datos · Jefe de Dominio
          </p>
        </div>
        <button className={styles.newBtn} onClick={() => navigate('/finalidades/nueva')}>
          + Nueva Solicitud
        </button>
      </div>

      {/* Filtros */}
      <div className={styles.filters}>
        {(['todas', 'pendiente', 'aprobada', 'rechazada'] as Filtro[]).map((f) => (
          <button
            key={f}
            className={[styles.filterBtn, filtro === f ? styles.filterActive : ''].join(' ')}
            onClick={() => setFiltro(f)}
          >
            {f === 'todas' ? 'Todas' : ESTADO_LABEL[f as SolicitudEstado]}
            <span className={styles.filterCount}>
              {counts[f as keyof typeof counts]}
            </span>
          </button>
        ))}
      </div>

      {/* Lista */}
      {lista.length === 0 ? (
        <div className={styles.empty}>
          <p>No hay solicitudes en esta categoría.</p>
        </div>
      ) : (
        <div className={styles.list}>
          {lista.map((sol) => (
            <div key={sol.id} className={styles.card}>
              <div className={styles.cardMain} onClick={() => setExpanded(expanded === sol.id ? null : sol.id)}>
                <div className={styles.cardTop}>
                  <span className={[styles.badge, styles[`badge_${sol.estado}`]].join(' ')}>
                    {ESTADO_LABEL[sol.estado]}
                  </span>
                  <span className={styles.dominioPill}>{sol.dominio}</span>
                  <span className={styles.fecha}>{sol.creadoEn}</span>
                </div>
                <h3 className={styles.cardTitle}>{sol.nombre}</h3>
                <p className={styles.cardDesc}>{sol.descripcion}</p>
                <div className={styles.cardMeta}>
                  <span>{sol.datos.length} dato{sol.datos.length !== 1 ? 's' : ''} definido{sol.datos.length !== 1 ? 's' : ''}</span>
                  <span className={styles.expandCue}>{expanded === sol.id ? '▲ Ocultar detalle' : '▼ Ver detalle'}</span>
                </div>
              </div>

              {/* Detalle expandible */}
              {expanded === sol.id && (
                <div className={styles.detail}>
                  <div className={styles.detailSection}>
                    <p className={styles.detailLabel}>Justificación</p>
                    <p className={styles.detailText}>{sol.justificacion}</p>
                  </div>

                  <div className={styles.detailSection}>
                    <p className={styles.detailLabel}>Datos requeridos</p>
                    <div className={styles.datosList}>
                      {sol.datos.map((d, i) => (
                        <div key={i} className={styles.datoRow}>
                          <div className={styles.datoTop}>
                            <span className={styles.datoTitulo}>{d.nombre}</span>
                            <span className={styles.tipoPill}>{d.tipo}</span>
                            <span className={d.obligatorio ? styles.badgeObligatorio : styles.badgeOpcional}>
                              {d.obligatorio ? 'Obligatorio' : 'Opcional'}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {sol.estado !== 'pendiente' && (
                    <div className={styles.revisionBox}>
                      <div className={styles.revisionHeader}>
                        <span className={styles.detailLabel}>
                          {sol.estado === 'aprobada' ? '✓ Aprobada por' : '✕ Rechazada por'} {sol.revisadoPor}
                        </span>
                        <span className={styles.revisionDate}>{sol.revisadoEn}</span>
                      </div>
                      {sol.notaRevision && (
                        <p className={styles.revisionNota}>{sol.notaRevision}</p>
                      )}
                      {sol.estado === 'aprobada' && sol.finalidadId && (
                        <p className={styles.revisionNota} style={{ fontStyle: 'normal', color: '#16a34a', fontWeight: 600 }}>
                          Finalidad creada: {sol.finalidadId}
                        </p>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default FinalidadesPage;
