import { useState, useMemo, useEffect } from 'react';
import { useAuth } from '../features/auth/AuthContext';
import { getAuditLogs, ApiError, type AuditLogDto } from '../api/auditApi';
import { formatDate, formatTime } from '../utils/formatters';
import styles from './AuditTrailPage.module.css';

type ActionCategory = 'all' | 'usuarios' | 'dominios' | 'finalidades' | 'solicitudes' | 'acuerdos';

const CATEGORY_LABELS: Record<ActionCategory, string> = {
  all:         'Todos',
  usuarios:    'Usuarios',
  dominios:    'Dominios',
  finalidades: 'Finalidades',
  solicitudes: 'Solicitudes',
  acuerdos:    'Acuerdos',
};

const ALL_CATEGORIES: ActionCategory[] = ['all', 'usuarios', 'dominios', 'finalidades', 'solicitudes', 'acuerdos'];

const matchesCategory = (log: AuditLogDto, cat: ActionCategory): boolean => {
  if (cat === 'all') return true;
  const a = log.action.toUpperCase();
  const t = log.tableName.toLowerCase();
  switch (cat) {
    case 'usuarios':    return a.includes('USUARIO')    || t.includes('user');
    case 'dominios':    return a.includes('DOMINIO')    || t.includes('domain');
    case 'finalidades': return a.includes('FINALIDAD')  || t.includes('purpose');
    case 'solicitudes': return a.includes('SOLICITUD')  || t.includes('request');
    case 'acuerdos':    return a.includes('AGREEMENT')  || a.includes('CONSENTIMIENTO') || t.includes('agreement');
    default: return true;
  }
};

const actionBadgeClass = (action: string): string => {
  const a = action.toUpperCase();
  if (a.includes('CREAR') || a.includes('APROBAR'))                                      return styles.badge_granted;
  if (a.includes('EDITAR') || a.includes('ACTUALIZAR'))                                  return styles.badge_updated;
  if (a.includes('RECHAZAR') || a.includes('BLOQUEAR') ||
      a.includes('DESACTIVAR') || a.includes('REVOCAR') || a.includes('ELIMINAR'))       return styles.badge_revoked;
  if (a.includes('EXPORTAR'))                                                             return styles.badge_exported;
  return styles.badge_viewed;
};

const actionIconClass = (action: string): string => {
  const a = action.toUpperCase();
  if (a.includes('CREAR') || a.includes('APROBAR'))                                      return styles.icon_granted;
  if (a.includes('EDITAR') || a.includes('ACTUALIZAR'))                                  return styles.icon_updated;
  if (a.includes('RECHAZAR') || a.includes('BLOQUEAR') ||
      a.includes('DESACTIVAR') || a.includes('REVOCAR') || a.includes('ELIMINAR'))       return styles.icon_revoked;
  if (a.includes('EXPORTAR'))                                                             return styles.icon_exported;
  return styles.icon_viewed;
};

const actionIconChar = (action: string): string => {
  const a = action.toUpperCase();
  if (a.includes('CREAR') || a.includes('APROBAR'))    return '✓';
  if (a.includes('EDITAR') || a.includes('ACTUALIZAR')) return '↻';
  if (a.includes('RECHAZAR') || a.includes('BLOQUEAR') ||
      a.includes('DESACTIVAR') || a.includes('REVOCAR') || a.includes('ELIMINAR')) return '✕';
  if (a.includes('EXPORTAR')) return '↑';
  return '◎';
};

const AuditTrailPage = () => {
  const { accessToken } = useAuth();

  const [logs, setLogs]               = useState<AuditLogDto[]>([]);
  const [loadingData, setLoadingData] = useState(true);
  const [loadError, setLoadError]     = useState<string | null>(null);

  const [search, setSearch]       = useState('');
  const [category, setCategory]   = useState<ActionCategory>('all');

  useEffect(() => {
    let cancelled = false;
    setLoadingData(true);
    setLoadError(null);
    getAuditLogs({ size: 100 }, accessToken)
      .then((res) => { if (!cancelled) setLogs(res.logs); })
      .catch((err) => {
        if (!cancelled)
          setLoadError(
            err instanceof ApiError
              ? err.message
              : 'No se pudo cargar el registro de auditoría.',
          );
      })
      .finally(() => { if (!cancelled) setLoadingData(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return logs.filter((log) => {
      if (!matchesCategory(log, category)) return false;
      if (q) {
        const searchable = [
          log.action,
          log.tableName,
          log.recordId ?? '',
          log.actorRole,
          log.ipAddress ?? '',
        ].join(' ').toLowerCase();
        if (!searchable.includes(q)) return false;
      }
      return true;
    });
  }, [logs, search, category]);

  const hasFilters = search || category !== 'all';
  const clearFilters = () => { setSearch(''); setCategory('all'); };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Registro de Auditoría</h2>
          <p className={styles.subtitle}>
            Registro inmutable de actividad para cumplimiento normativo — Ley 21.719
          </p>
        </div>
      </div>

      {/* Category chips */}
      <div className={styles.statsRow}>
        {ALL_CATEGORIES.map((cat) => (
          <button
            key={cat}
            className={[styles.statChip, category === cat ? styles.statChipActive : ''].join(' ')}
            onClick={() => setCategory(cat)}
          >
            <span className={styles.statLabel}>{CATEGORY_LABELS[cat]}</span>
            {!loadingData && (
              <span className={styles.statCount}>
                {cat === 'all'
                  ? logs.length
                  : logs.filter((l) => matchesCategory(l, cat)).length}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Search / filter bar */}
      <div className={styles.filterBar}>
        <div className={styles.searchBox}>
          <svg className={styles.searchIcon} width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            type="text"
            placeholder="Buscar por acción, tabla, entidad, rol o IP..."
            className={styles.searchInput}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button className={styles.clearBtn} onClick={() => setSearch('')}>✕</button>
          )}
        </div>
        <div className={styles.filterRight}>
          <span className={styles.resultCount}>
            {loadingData
              ? 'Cargando…'
              : `${filtered.length} evento${filtered.length !== 1 ? 's' : ''}`}
            {hasFilters && !loadingData && ' (filtrado)'}
          </span>
          {hasFilters && (
            <button className={styles.clearAll} onClick={clearFilters}>Limpiar filtros</button>
          )}
        </div>
      </div>

      {/* Table */}
      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {loadingData ? (
            <div className={styles.empty}>
              <p>Cargando eventos de auditoría…</p>
            </div>
          ) : loadError ? (
            <div className={styles.empty}>
              <p>{loadError}</p>
            </div>
          ) : filtered.length === 0 ? (
            <div className={styles.empty}>
              <span className={styles.emptyIcon}>🔍</span>
              <p>No se encontraron eventos{hasFilters ? ' con los filtros aplicados.' : '.'}</p>
              {hasFilters && (
                <button className={styles.clearAll} onClick={clearFilters}>Limpiar filtros</button>
              )}
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Evento</th>
                  <th>Acción</th>
                  <th>Tabla · Entidad</th>
                  <th>Actor</th>
                  <th>Dirección IP</th>
                  <th>Fecha y hora</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((log) => (
                  <tr key={log.id}>
                    <td>
                      <div className={styles.eventCell}>
                        <span className={[styles.actionIcon, actionIconClass(log.action)].join(' ')}>
                          {actionIconChar(log.action)}
                        </span>
                        <span className={styles.eventId}>{log.id.slice(0, 8).toUpperCase()}</span>
                      </div>
                    </td>
                    <td>
                      <span className={[styles.badge, actionBadgeClass(log.action)].join(' ')}>
                        <span className={styles.badgeDot} />
                        {log.action}
                      </span>
                    </td>
                    <td>
                      <div className={styles.actorCell}>
                        <span className={styles.actorName}>{log.tableName.toUpperCase()}</span>
                        <span className={styles.actorEmail}>
                          {log.recordId ? log.recordId.slice(0, 8).toUpperCase() + '…' : '—'}
                        </span>
                      </div>
                    </td>
                    <td>
                      <div className={styles.actorCell}>
                        <span className={styles.actorName}>{log.actorRole}</span>
                        <span className={styles.actorEmail}>
                          {log.actorId.slice(0, 8).toUpperCase()}…
                        </span>
                      </div>
                    </td>
                    <td className={styles.cellMono}>{log.ipAddress ?? '—'}</td>
                    <td className={styles.cellDate}>
                      <div className={styles.timestampCell}>
                        <span>{formatDate(log.createdAt)}</span>
                        <span className={styles.timestampTime}>{formatTime(log.createdAt)}</span>
                      </div>
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

export default AuditTrailPage;
