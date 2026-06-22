import { useState } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import {
  SOLICITUDES, aprobarSolicitud, rechazarSolicitud, marcarEnRevision, getDocumentosVigentes,
  type SolicitudEstado, type SolicitudFinalidad,
} from '../utils/mockData';
import styles from './AprobacionFinalidadesPage.module.css';

type Filtro = 'todas' | SolicitudEstado;

const ESTADO_LABEL: Record<SolicitudEstado, string> = {
  pendiente:   'Pendiente',
  en_revision: 'En revisión',
  aprobada:    'Aprobada',
  rechazada:   'Rechazada',
};

const AprobacionFinalidadesPage = () => {
  const { user } = useAuth();
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [expanded, setExpanded] = useState<string | null>(null);
  const [rechazandoId, setRechazandoId] = useState<string | null>(null);
  const [notaRechazo, setNotaRechazo] = useState('');
  const [notaError, setNotaError] = useState('');
  const [aprobandoId, setAprobandoId] = useState<string | null>(null);
  const [docSeleccionado, setDocSeleccionado] = useState('');
  const [docError, setDocError] = useState('');
  const [, forceUpdate] = useState(0);

  const documentos = getDocumentosVigentes();

  const lista = filtro === 'todas' ? SOLICITUDES : SOLICITUDES.filter((s) => s.estado === filtro);

  const counts = {
    todas:       SOLICITUDES.length,
    pendiente:   SOLICITUDES.filter((s) => s.estado === 'pendiente').length,
    en_revision: SOLICITUDES.filter((s) => s.estado === 'en_revision').length,
    aprobada:    SOLICITUDES.filter((s) => s.estado === 'aprobada').length,
    rechazada:   SOLICITUDES.filter((s) => s.estado === 'rechazada').length,
  };

  const handleIniciarAprobacion = (id: string) => {
    setAprobandoId(id);
    setDocSeleccionado('');
    setDocError('');
    setRechazandoId(null);
  };

  const handleConfirmarAprobacion = (sol: SolicitudFinalidad) => {
    if (!docSeleccionado) {
      setDocError('Debes seleccionar un Documento de Privacidad.');
      return;
    }
    aprobarSolicitud(sol.id, user?.name ?? 'DPO', docSeleccionado);
    setAprobandoId(null);
    setExpanded(null);
    forceUpdate((n) => n + 1);
  };

  const handleIniciarRechazo = (id: string) => {
    setRechazandoId(id);
    setNotaRechazo('');
    setNotaError('');
    setAprobandoId(null);
  };

  const handleConfirmarRechazo = (id: string) => {
    if (!notaRechazo.trim()) {
      setNotaError('Debes ingresar un motivo de rechazo.');
      return;
    }
    rechazarSolicitud(id, user?.name ?? 'DPO', notaRechazo);
    setRechazandoId(null);
    setExpanded(null);
    forceUpdate((n) => n + 1);
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Finalidades de Tratamiento</h2>
          <p className={styles.subtitle}>
            Revisa y aprueba o rechaza las solicitudes enviadas por los Jefes de Dominio. Al aprobar, debes seleccionar el Documento de Privacidad que respaldará la finalidad.
          </p>
        </div>
        {(counts.pendiente + counts.en_revision) > 0 && (
          <div className={styles.pendienteAlert}>
            {counts.pendiente + counts.en_revision} solicitud{(counts.pendiente + counts.en_revision) !== 1 ? 'es' : ''} por resolver
          </div>
        )}
      </div>

      {/* Filtros */}
      <div className={styles.filters}>
        {(['todas', 'pendiente', 'en_revision', 'aprobada', 'rechazada'] as Filtro[]).map((f) => (
          <button
            key={f}
            className={[styles.filterBtn, filtro === f ? styles.filterActive : ''].join(' ')}
            onClick={() => setFiltro(f)}
          >
            {f === 'todas' ? 'Todas' : ESTADO_LABEL[f as SolicitudEstado]}
            <span className={styles.filterCount}>{counts[f as keyof typeof counts]}</span>
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
            <div
              key={sol.id}
              className={[
                styles.card,
                sol.estado === 'pendiente'   ? styles.cardPendiente   : '',
                sol.estado === 'en_revision' ? styles.cardEnRevision  : '',
              ].join(' ')}
            >
              {/* Cabecera */}
              <div
                className={styles.cardMain}
                onClick={() => {
                  if (rechazandoId === sol.id || aprobandoId === sol.id) return;
                  const next = expanded === sol.id ? null : sol.id;
                  if (next && sol.estado === 'pendiente') {
                    marcarEnRevision(sol.id);
                    forceUpdate((n) => n + 1);
                  }
                  setExpanded(next);
                }}
              >
                <div className={styles.cardTop}>
                  <span className={[styles.badge, styles[`badge_${sol.estado}`]].join(' ')}>
                    {ESTADO_LABEL[sol.estado]}
                  </span>
                  <span className={styles.dominioPill}>{sol.dominio}</span>
                  <span className={styles.cardMeta}>
                    Solicitado por <strong>{sol.creadoPor}</strong> · {sol.creadoEn}
                  </span>
                </div>
                <h3 className={styles.cardTitle}>{sol.nombre}</h3>
                <p className={styles.cardDesc}>{sol.descripcion}</p>
                <div className={styles.cardFooter}>
                  <span className={styles.datosCount}>
                    {sol.datos.length} dato{sol.datos.length !== 1 ? 's' : ''} definido{sol.datos.length !== 1 ? 's' : ''}
                  </span>
                  <span className={styles.expandCue}>
                    {expanded === sol.id ? '▲ Ocultar' : '▼ Ver detalle y acciones'}
                  </span>
                </div>
              </div>

              {/* Panel de detalle */}
              {expanded === sol.id && (
                <div className={styles.detail}>
                  {/* Justificación */}
                  <div className={styles.detailSection}>
                    <p className={styles.detailLabel}>Justificación del Jefe de Dominio</p>
                    <p className={styles.detailText}>{sol.justificacion}</p>
                  </div>

                  {/* Datos requeridos */}
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

                  {/* Revisión previa (no pendiente) */}
                  {sol.estado !== 'pendiente' && (
                    <div className={[styles.revisionBox, sol.estado === 'aprobada' ? styles.revisionAprobada : styles.revisionRechazada].join(' ')}>
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
                        <p className={styles.finalidadRef}>Finalidad creada: <strong>{sol.finalidadId}</strong></p>
                      )}
                    </div>
                  )}

                  {/* Acciones (pendientes y en revisión) */}
                  {(sol.estado === 'pendiente' || sol.estado === 'en_revision') && (
                    <div className={styles.actions}>
                      {aprobandoId === sol.id ? (
                        <div className={styles.aprobarForm}>
                          <p className={styles.aprobarFormTitle}>Selecciona el Documento de Privacidad que respaldará esta finalidad</p>
                          <select
                            className={[styles.docSelect, docError ? styles.docSelectError : ''].join(' ')}
                            value={docSeleccionado}
                            onChange={(e) => { setDocSeleccionado(e.target.value); setDocError(''); }}
                          >
                            <option value="">— Selecciona un documento —</option>
                            {documentos.map((doc) => (
                              <option key={doc.id} value={doc.id}>
                                {doc.nombre} (v{doc.version})
                              </option>
                            ))}
                          </select>
                          {docError && <p className={styles.docErrorMsg}>{docError}</p>}
                          <div className={styles.aprobarActions}>
                            <button className={styles.aprobarBtn} onClick={() => handleConfirmarAprobacion(sol)}>
                              ✓ Confirmar aprobación
                            </button>
                            <button className={styles.cancelRechazar} onClick={() => setAprobandoId(null)}>
                              Cancelar
                            </button>
                          </div>
                        </div>
                      ) : rechazandoId === sol.id ? (
                        <div className={styles.rechazarForm}>
                          <label className={styles.rechazarLabel}>
                            Motivo de rechazo <span className={styles.req}>*</span>
                          </label>
                          <textarea
                            className={[styles.rechazarTextarea, notaError ? styles.rechazarError : ''].join(' ')}
                            value={notaRechazo}
                            onChange={(e) => { setNotaRechazo(e.target.value); setNotaError(''); }}
                            placeholder="Indica el motivo de rechazo. Ej: No cumple con los requisitos del Art. 14 Ley 21.719…"
                            rows={3}
                            maxLength={500}
                            autoFocus
                          />
                          {notaError && <p className={styles.rechazarErrorMsg}>{notaError}</p>}
                          <div className={styles.rechazarActions}>
                            <button className={styles.confirmRechazar} onClick={() => handleConfirmarRechazo(sol.id)}>
                              Confirmar rechazo
                            </button>
                            <button className={styles.cancelRechazar} onClick={() => setRechazandoId(null)}>
                              Cancelar
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className={styles.actionBtns}>
                          <button className={styles.aprobarBtn} onClick={() => handleIniciarAprobacion(sol.id)}>
                            ✓ Aprobar finalidad
                          </button>
                          <button className={styles.rechazarBtn} onClick={() => handleIniciarRechazo(sol.id)}>
                            ✕ Rechazar
                          </button>
                        </div>
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

export default AprobacionFinalidadesPage;
