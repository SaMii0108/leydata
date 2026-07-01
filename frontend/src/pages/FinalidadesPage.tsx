import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { getPurposes, ApiError } from '../api/purposesApi';
import type { PurposeResponse } from '../api/purposesApi';
import Button from '../components/common/Button';
import styles from './FinalidadesPage.module.css';

const FinalidadesPage = () => {
  const { user, accessToken } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [purposes, setPurposes] = useState<PurposeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [domainFilter, setDomainFilter] = useState('');
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    const state = location.state as { purposeCreated?: boolean } | null;
    if (state?.purposeCreated) {
      setSuccessMsg('Finalidad creada correctamente.');
      const tid = setTimeout(() => setSuccessMsg(null), 4000);
      window.history.replaceState({}, '');
      return () => clearTimeout(tid);
    }
  }, [location.state]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getPurposes(accessToken)
      .then((data) => { if (!cancelled) setPurposes(data); })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof ApiError ? err.message : 'No se pudieron cargar las finalidades');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [accessToken]);

  const domains = useMemo(() => {
    const seen = new Map<string, string>();
    for (const p of purposes) {
      if (!seen.has(p.domainId)) seen.set(p.domainId, p.domainName);
    }
    return Array.from(seen.entries()).map(([id, name]) => ({ id, name }));
  }, [purposes]);

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return purposes.filter((p) => {
      const matchSearch = !q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q);
      const matchDomain = !domainFilter || p.domainId === domainFilter;
      return matchSearch && matchDomain;
    });
  }, [purposes, search, domainFilter]);

  const isDpo = user?.role === 'DPO';

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Finalidades</h2>
          <p className={styles.subtitle}>Finalidades de tratamiento de datos registradas en el sistema</p>
        </div>
        {isDpo && (
          <Button variant="primary" size="sm" onClick={() => navigate('/finalidades/nueva')}>
            + Nueva Finalidad
          </Button>
        )}
      </div>

      <div className={styles.filterBar}>
        <input
          type="text"
          className={styles.searchInput}
          placeholder="Buscar por nombre o código…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {domains.length > 1 && (
          <select
            className={styles.domainSelect}
            value={domainFilter}
            onChange={(e) => setDomainFilter(e.target.value)}
          >
            <option value="">Todos los dominios</option>
            {domains.map((d) => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
        )}
      </div>

      {successMsg && (
        <div className={styles.successBanner}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="9 12 11 14 15 10"/>
          </svg>
          {successMsg}
        </div>
      )}

      {error && (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--color-danger)' }}>{error}</p>
      )}

      <section className={styles.tableSection}>
        <div className={styles.tableWrapper}>
          {loading ? (
            <p className={styles.stateMsg}>Cargando finalidades…</p>
          ) : filtered.length === 0 ? (
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"/>
                  <circle cx="12" cy="12" r="6"/>
                  <circle cx="12" cy="12" r="2"/>
                </svg>
              </div>
              <p className={styles.emptyTitle}>
                {purposes.length === 0 ? 'Sin finalidades registradas' : 'Sin resultados'}
              </p>
              <p className={styles.emptyHint}>
                {purposes.length === 0
                  ? isDpo
                    ? 'Crea la primera finalidad con el botón "Nueva Finalidad".'
                    : 'No hay finalidades registradas en el sistema.'
                  : 'Ajusta los filtros para ver más resultados.'}
              </p>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Código</th>
                  <th>Dominio</th>
                  <th>Base legal</th>
                  <th>Requerida</th>
                  <th>Revocable</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((p) => (
                  <tr key={p.id}>
                    <td className={styles.cellName}>
                      {p.name}
                      {p.locked && (
                        <span className={styles.lockBadge} title="Publicada en un documento de privacidad">
                          Bloqueada
                        </span>
                      )}
                    </td>
                    <td><span className={styles.codePill}>{p.code}</span></td>
                    <td className={styles.cellSecondary}>{p.domainName}</td>
                    <td className={styles.cellSecondary}>{p.legalBasisName}</td>
                    <td>
                      <span className={p.required ? styles.yes : styles.no}>
                        {p.required ? 'Sí' : 'No'}
                      </span>
                    </td>
                    <td>
                      <span className={p.revocable ? styles.yes : styles.no}>
                        {p.revocable ? 'Sí' : 'No'}
                      </span>
                    </td>
                    <td>
                      <span className={[styles.statusBadge, p.isActive ? styles.status_active : styles.status_inactive].join(' ')}>
                        {p.isActive ? 'Activa' : 'Inactiva'}
                      </span>
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

export default FinalidadesPage;
