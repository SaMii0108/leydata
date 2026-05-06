import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import ConsentDrawer from '../components/common/ConsentDrawer';
import { consentRecords as initialRecords } from '../utils/mockData';
import type { ConsentStatus, ConsentRecord } from '../utils/mockData';
import { usePermissions } from '../features/auth/usePermissions';
import { AREAS } from '../features/auth/mockUsers';
import styles from './ConsentimientosPage.module.css';

type SortField = Extract<keyof ConsentRecord, 'area' | 'finalidad' | 'estado' | 'fechaOtorgamiento' | 'fechaExpiracion'>;
type SortDir = 'asc' | 'desc';

const ALL_STATUSES: ConsentStatus[] = ['activo', 'revocado', 'pendiente', 'expirado'];

const ConsentimientosPage = () => {
  const navigate = useNavigate();
  const { canCreate, canViewAll, canViewDetail, userArea } = usePermissions();

  const baseRecords = useMemo(
    () => canViewAll ? initialRecords : initialRecords.filter((r) => r.area === userArea),
    [canViewAll, userArea],
  );

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<ConsentStatus | 'todos'>('todos');
  const [areaFilter, setAreaFilter] = useState('todas');
  const [sortField, setSortField] = useState<SortField>('fechaOtorgamiento');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [drawerRecord, setDrawerRecord] = useState<ConsentRecord | null>(null);

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return [...baseRecords]
      .filter((r) => {
        if (statusFilter !== 'todos' && r.estado !== statusFilter) return false;
        if (areaFilter !== 'todas' && r.area !== areaFilter) return false;
        if (q && !r.id.toLowerCase().includes(q) && !r.finalidad.toLowerCase().includes(q) && !r.area.toLowerCase().includes(q)) return false;
        return true;
      })
      .sort((a, b) => {
        const av = a[sortField];
        const bv = b[sortField];
        const cmp = av < bv ? -1 : av > bv ? 1 : 0;
        return sortDir === 'asc' ? cmp : -cmp;
      });
  }, [baseRecords, search, statusFilter, areaFilter, sortField, sortDir]);

  const handleSort = (field: SortField) => {
    if (sortField === field) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else { setSortField(field); setSortDir('asc'); }
  };

  const clearFilters = () => { setSearch(''); setStatusFilter('todos'); setAreaFilter('todas'); };
  const hasActiveFilters = search || statusFilter !== 'todos' || areaFilter !== 'todas';

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
            placeholder="Buscar por ID, finalidad o área..."
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
                  {s.charAt(0).toUpperCase() + s.slice(1)}
                </button>
              ))}
            </div>
          </div>

          {canViewAll && (
            <div className={styles.filterGroup}>
              <label className={styles.filterLabel} htmlFor="area-select">Área</label>
              <select
                id="area-select"
                className={styles.select}
                value={areaFilter}
                onChange={(e) => setAreaFilter(e.target.value)}
              >
                <option value="todas">Todas</option>
                {AREAS.map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            </div>
          )}

          {hasActiveFilters && (
            <button className={styles.clearAll} onClick={clearFilters}>Limpiar filtros</button>
          )}
        </div>
      </section>

      {/* Table */}
      <section className={styles.tableSection}>
        <div className={styles.tableHeader}>
          <span className={styles.resultCount}>
            {filtered.length} resultado{filtered.length !== 1 ? 's' : ''}
            {hasActiveFilters && ' (filtrado)'}
          </span>
          <div className={styles.tableActions}>
            <Button variant="ghost" size="sm">Exportar CSV</Button>
          </div>
        </div>

        <div className={styles.tableWrapper}>
          {filtered.length === 0 ? (
            <div className={styles.empty}>
              <span className={styles.emptyIcon}>📭</span>
              <p>No se encontraron registros con los filtros aplicados.</p>
              <button className={styles.clearAll} onClick={clearFilters}>Limpiar filtros</button>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>ID</th>
                  <th className={styles.sortable} onClick={() => handleSort('area')}>
                    Área {sortIcon('area')}
                  </th>
                  <th className={styles.sortable} onClick={() => handleSort('finalidad')}>
                    Finalidad {sortIcon('finalidad')}
                  </th>
                  <th className={styles.sortable} onClick={() => handleSort('estado')}>
                    Estado {sortIcon('estado')}
                  </th>
                  <th className={styles.sortable} onClick={() => handleSort('fechaOtorgamiento')}>
                    Otorgamiento {sortIcon('fechaOtorgamiento')}
                  </th>
                  <th className={styles.sortable} onClick={() => handleSort('fechaExpiracion')}>
                    Expiración {sortIcon('fechaExpiracion')}
                  </th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((record) => (
                  <tr key={record.id}>
                    <td className={styles.cellId}>{record.id}</td>
                    <td>
                      <span className={styles.areaBadge}>{record.area}</span>
                    </td>
                    <td className={styles.cellFinalidad}>{record.finalidad}</td>
                    <td><Badge status={record.estado} /></td>
                    <td className={styles.cellDate}>{formatDate(record.fechaOtorgamiento)}</td>
                    <td className={styles.cellDate}>{formatDate(record.fechaExpiracion)}</td>
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
          )}
        </div>
      </section>

      <ConsentDrawer record={drawerRecord} onClose={() => setDrawerRecord(null)} />
    </div>
  );
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

export default ConsentimientosPage;
