import { useState, useMemo } from 'react';
import { auditEvents } from '../utils/mockData';
import type { AuditAction } from '../utils/mockData';
import { usePermissions } from '../features/auth/usePermissions';
import Button from '../components/common/Button';
import { ACTION_LABEL } from '../constants/labels';
import { formatDate, formatTime } from '../utils/formatters';
import styles from './AuditTrailPage.module.css';

const ALL_ACTIONS: AuditAction[] = ['granted', 'revoked', 'updated', 'viewed', 'exported', 'deleted'];

const ActionBadge = ({ action }: { action: AuditAction }) => (
  <span className={[styles.badge, styles[`badge_${action}`]].join(' ')}>
    <span className={styles.badgeDot} />
    {ACTION_LABEL[action]}
  </span>
);

const ActionIcon = ({ action }: { action: AuditAction }) => {
  const icons: Record<AuditAction, string> = {
    granted:  '✓',
    revoked:  '✕',
    updated:  '↻',
    viewed:   '◎',
    exported: '↑',
    deleted:  '⊗',
  };
  return (
    <span className={[styles.actionIcon, styles[`icon_${action}`]].join(' ')}>
      {icons[action]}
    </span>
  );
};

const AuditTrailPage = () => {
  const { role } = usePermissions();
  const isDpo = role === 'DPO';

  const [search, setSearch]             = useState('');
  const [actionFilter, setActionFilter] = useState<AuditAction | 'all'>('all');

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return auditEvents.filter((e) => {
      if (actionFilter !== 'all' && e.action !== actionFilter) return false;
      if (q) {
        // DPO solo puede buscar por ID de evento, descripción e ID de consentimiento
        const searchableFields = isDpo
          ? [e.id, e.description, e.consentId]
          : [e.actor, e.actorEmail, e.id, e.description, e.consentId];
        if (!searchableFields.some((v) => v.toLowerCase().includes(q))) return false;
      }
      return true;
    });
  }, [search, actionFilter, isDpo]);

  const hasFilters = search || actionFilter !== 'all';

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Registro de Auditoría</h2>
          <p className={styles.subtitle}>
            Registro completo de actividad para cumplimiento normativo — Ley 21.719
          </p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => console.warn('Exportar registro: pendiente integración backend')}
        >
          Exportar registro
        </Button>
      </div>

      {/* Aviso vista DPO */}
      {isDpo && (
        <div className={styles.anonBanner}>
          <span className={styles.anonIcon}>🔒</span>
          <span>
            <strong>Vista protegida:</strong> la información de los actores no se muestra.
            Los registros se identifican únicamente por código de consentimiento.
          </span>
        </div>
      )}

      <div className={styles.statsRow}>
        {ALL_ACTIONS.map((action) => {
          const count = auditEvents.filter((e) => e.action === action).length;
          return (
            <button
              key={action}
              className={[styles.statChip, actionFilter === action ? styles.statChipActive : ''].join(' ')}
              onClick={() => setActionFilter(actionFilter === action ? 'all' : action)}
            >
              <span className={[styles.statDot, styles[`dot_${action}`]].join(' ')} />
              <span className={styles.statLabel}>{ACTION_LABEL[action]}</span>
              <span className={styles.statCount}>{count}</span>
            </button>
          );
        })}
      </div>

      <div className={styles.filterBar}>
        <div className={styles.searchBox}>
          <svg className={styles.searchIcon} width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            type="text"
            placeholder={isDpo
              ? 'Buscar por ID de evento, código de consentimiento o descripción...'
              : 'Buscar por actor, ID de consentimiento o descripción...'}
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
            {filtered.length} evento{filtered.length !== 1 ? 's' : ''}
            {hasFilters && ' (filtrado)'}
          </span>
          {hasFilters && (
            <button className={styles.clearAll} onClick={() => { setSearch(''); setActionFilter('all'); }}>
              Limpiar filtros
            </button>
          )}
        </div>
      </div>

      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {filtered.length === 0 ? (
            <div className={styles.empty}>
              <span className={styles.emptyIcon}>🔍</span>
              <p>No se encontraron eventos con los filtros aplicados.</p>
              <button className={styles.clearAll} onClick={() => { setSearch(''); setActionFilter('all'); }}>
                Limpiar filtros
              </button>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Evento</th>
                  <th>Acción</th>
                  <th>Descripción</th>
                  {/* DPO no ve columna de actor — solo el código de consentimiento identifica el registro */}
                  {!isDpo && <th>Actor</th>}
                  <th>Cód. Consentimiento</th>
                  <th>Dirección IP</th>
                  <th>Fecha y hora</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((event) => (
                  <tr key={event.id}>
                    <td>
                      <div className={styles.eventCell}>
                        <ActionIcon action={event.action} />
                        <span className={styles.eventId}>{event.id}</span>
                      </div>
                    </td>
                    <td><ActionBadge action={event.action} /></td>
                    <td className={styles.cellDesc}>{event.description}</td>
                    {!isDpo && (
                      <td>
                        <div className={styles.actorCell}>
                          <span className={styles.actorName}>{event.actor}</span>
                          <span className={styles.actorEmail}>{event.actorEmail}</span>
                        </div>
                      </td>
                    )}
                    <td className={styles.cellMono}>{event.consentId}</td>
                    <td className={styles.cellMono}>{event.ipAddress}</td>
                    <td className={styles.cellDate}>
                      <div className={styles.timestampCell}>
                        <span>{formatDate(event.timestamp)}</span>
                        <span className={styles.timestampTime}>{formatTime(event.timestamp)}</span>
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
