import { useState, useMemo, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Badge from '../components/common/Badge';
import type { BadgeStatus } from '../components/common/Badge';
import Button from '../components/common/Button';
import ConsentDrawer from '../components/common/ConsentDrawer';
import {
  getAgreements,
  ApiError,
  type AgreementResponse,
  type AgreementStatus,
} from '../api/agreementsApi';
import { usePermissions } from '../features/auth/usePermissions';
import { useAuth } from '../features/auth/AuthContext';
import { formatDate } from '../utils/formatters';
import styles from './ConsentimientosPage.module.css';

type SortField = 'status' | 'createdAt' | 'expiration';
type SortDir   = 'asc' | 'desc';

const ALL_STATUSES: AgreementStatus[] = ['ACTIVE', 'REVOKED', 'EXPIRED'];
const PAGE_SIZE = 10;

const STATUS_BADGE: Record<AgreementStatus, BadgeStatus> = {
  ACTIVE:  'ACTIVE',
  REVOKED: 'REVOKED',
  EXPIRED: 'EXPIRED',
};

const STATUS_LABEL: Record<AgreementStatus, string> = {
  ACTIVE:  'Activo',
  REVOKED: 'Revocado',
  EXPIRED: 'Expirado',
};

const firstPurposeName = (r: AgreementResponse) => r.purposes[0]?.purposeName ?? '—';

const ConsentimientosPage = () => {
  const navigate = useNavigate();
  const { accessToken } = useAuth();
  const { canCreate, canViewDetail } = usePermissions();

  const [agreements, setAgreements]   = useState<AgreementResponse[]>([]);
  const [loadingData, setLoadingData] = useState(true);
  const [loadError, setLoadError]     = useState<string | null>(null);

  const [search, setSearch]               = useState('');
  const [statusFilter, setStatusFilter]   = useState<AgreementStatus | 'todos'>('todos');
  const [sortField, setSortField]         = useState<SortField>('createdAt');
  const [sortDir, setSortDir]             = useState<SortDir>('desc');
  const [drawerRecord, setDrawerRecord]   = useState<AgreementResponse | null>(null);
  const [currentPage, setCurrentPage]     = useState(1);

  useEffect(() => {
    let cancelled = false;
    setLoadingData(true);
    setLoadError(null);
    getAgreements(undefined, accessToken)
      .then((data) => { if (!cancelled) setAgreements(data); })
      .catch((err) => {
        if (!cancelled)
          setLoadError(
            err instanceof ApiError
              ? err.message
              : 'No se pudo cargar el registro de consentimientos.',
          );
      })
      .finally(() => { if (!cancelled) setLoadingData(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return [...agreements]
      .filter((r) => {
        if (statusFilter !== 'todos' && r.status !== statusFilter) return false;
        if (q) {
          const inId       = r.id.toLowerCase().includes(q);
          const inSubject  = r.dataSubjectId?.toLowerCase().includes(q) ?? false;
          const inPurposes = r.purposes.some((p) => p.purposeName.toLowerCase().includes(q));
          if (!inId && !inSubject && !inPurposes) return false;
        }
        return true;
      })
      .sort((a, b) => {
        const av = sortField === 'expiration' ? (a.expiration ?? '') :
                   sortField === 'status'     ? a.status : a.createdAt;
        const bv = sortField === 'expiration' ? (b.expiration ?? '') :
                   sortField === 'status'     ? b.status : b.createdAt;
        const cmp = av < bv ? -1 : av > bv ? 1 : 0;
        return sortDir === 'asc' ? cmp : -cmp;
      });
  }, [agreements, search, statusFilter, sortField, sortDir]);

  useEffect(() => { setCurrentPage(1); }, [search, statusFilter, sortField, sortDir]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const paginated  = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const handleSort = (field: SortField) => {
    if (sortField === field) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else { setSortField(field); setSortDir('asc'); }
  };

  const clearFilters = () => { setSearch(''); setStatusFilter('todos'); };
  const hasActiveFilters = search || statusFilter !== 'todos';

  const sortIcon = (field: SortField) => {
    if (sortField !== field) return <span className={styles.sortNeutral}>↕</span>;
    return <span className={styles.sortActive}>{sortDir === 'asc' ? '↑' : '↓'}</span>;
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Consentimientos</h2>
          <p className={styles.subtitle}>Registro de consentimientos bajo Ley 21.719</p>
        </div>
        {canCreate && (
          <Button variant="primary" onClick={() => navigate('/consentimientos/nuevo')}>
            + Nuevo consentimiento
          </Button>
        )}
      </div>

      {/* Filter bar */}
      <section className={styles.filterBar}>
        <div className={styles.searchBox}>
          <span className={styles.searchIcon}>⌕</span>
          <input
            type="text"
            placeholder="Buscar por ID, titular o finalidad..."
            className={styles.searchInput}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button className={styles.clearInput} onClick={() => setSearch('')} aria-label="Limpiar">✕</button>
          )}
        </div>

        <div className={styles.filters}>
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>Estado</label>
            <div className={styles.pills}>
              <button
                className={[styles.pill, statusFilter === 'todos' ? styles.pillActive : ''].join(' ')}
                onClick={() => setStatusFilter('todos')}
              >
                Todos
              </button>
              {ALL_STATUSES.map((s) => (
                <button
                  key={s}
                  className={[styles.pill, statusFilter === s ? styles.pillActive : ''].join(' ')}
                  onClick={() => setStatusFilter(s)}
                >
                  {STATUS_LABEL[s]}
                </button>
              ))}
            </div>
          </div>

          {hasActiveFilters && (
            <button className={styles.clearAll} onClick={clearFilters}>Limpiar filtros</button>
          )}
        </div>
      </section>

      {/* Table */}
      <section className={styles.tableSection}>
        <div className={styles.tableHeader}>
          <span className={styles.resultCount}>
            {loadingData
              ? 'Cargando…'
              : `${filtered.length} resultado${filtered.length !== 1 ? 's' : ''}`}
            {hasActiveFilters && !loadingData && ' (filtrado)'}
          </span>
        </div>

        <div className={styles.tableWrapper}>
          {loadingData ? (
            <div className={styles.empty}>
              <p>Cargando consentimientos…</p>
            </div>
          ) : loadError ? (
            <div className={styles.empty}>
              <p className={styles.emptyTitle}>{loadError}</p>
            </div>
          ) : agreements.length === 0 ? (
            <div className={styles.empty}>
              <svg className={styles.emptyIcon} width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              <p className={styles.emptyTitle}>No hay consentimientos registrados</p>
              <p className={styles.emptyHint}>Cuando se creen acuerdos de consentimiento aparecerán aquí.</p>
            </div>
          ) : filtered.length === 0 ? (
            <div className={styles.empty}>
              <svg className={styles.emptyIcon} width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                <line x1="8" y1="11" x2="14" y2="11"/>
              </svg>
              <p>No se encontraron registros con los filtros aplicados.</p>
              <button className={styles.clearAll} onClick={clearFilters}>Limpiar filtros</button>
            </div>
          ) : (
            <>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Titular</th>
                    <th>Finalidad principal</th>
                    <th className={styles.sortable} onClick={() => handleSort('status')}>
                      Estado {sortIcon('status')}
                    </th>
                    <th className={styles.sortable} onClick={() => handleSort('createdAt')}>
                      Registro {sortIcon('createdAt')}
                    </th>
                    <th className={styles.sortable} onClick={() => handleSort('expiration')}>
                      Expiración {sortIcon('expiration')}
                    </th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {paginated.map((record) => (
                    <tr key={record.id}>
                      <td className={styles.cellId}>{record.id.slice(0, 8).toUpperCase()}</td>
                      <td className={styles.cellMuted}>
                        {record.dataSubjectId
                          ? record.dataSubjectId.slice(0, 8).toUpperCase() + '…'
                          : '—'}
                      </td>
                      <td className={styles.cellFinalidad}>{firstPurposeName(record)}</td>
                      <td><Badge status={STATUS_BADGE[record.status]} /></td>
                      <td className={styles.cellDate}>{formatDate(record.createdAt)}</td>
                      <td className={styles.cellDate}>
                        {record.expiration ? formatDate(record.expiration) : '—'}
                      </td>
                      <td>
                        {canViewDetail && (
                          <Button variant="ghost" size="sm" onClick={() => setDrawerRecord(record)}>
                            Ver
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {totalPages > 1 && (
                <div className={styles.pagination}>
                  <button
                    className={styles.pageBtn}
                    disabled={currentPage === 1}
                    onClick={() => setCurrentPage((p) => p - 1)}
                  >
                    ← Anterior
                  </button>
                  <span className={styles.pageInfo}>
                    Página {currentPage} de {totalPages}
                    <span className={styles.pageTotal}> · {filtered.length} registros</span>
                  </span>
                  <button
                    className={styles.pageBtn}
                    disabled={currentPage === totalPages}
                    onClick={() => setCurrentPage((p) => p + 1)}
                  >
                    Siguiente →
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </section>

      <ConsentDrawer record={drawerRecord} onClose={() => setDrawerRecord(null)} />
    </div>
  );
};

export default ConsentimientosPage;
