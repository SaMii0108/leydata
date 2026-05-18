import { useState, useMemo } from 'react';
import { consentRecords as initialRecords } from '../utils/mockData';
import type { ConsentRecord, ConsentStatus } from '../utils/mockData';
import { useAuth } from '../features/auth/AuthContext';
import RevokeModal from '../components/common/RevokeModal';
import ConsentCard from '../components/common/ConsentCard';
import styles from './TitularPortalPage.module.css';

const ALL_STATUSES: { value: ConsentStatus | 'todos'; label: string }[] = [
  { value: 'todos',    label: 'Todos' },
  { value: 'activo',   label: 'Activos' },
  { value: 'revocado', label: 'Revocados' },
  { value: 'expirado', label: 'Expirados' },
  { value: 'pendiente',label: 'Pendientes' },
];

const TitularPortalPage = () => {
  const { user } = useAuth();
  const [records, setRecords] = useState<ConsentRecord[]>(
    initialRecords.filter((r) => r.titularId === user?.id),
  );
  const [revokeTarget, setRevokeTarget] = useState<ConsentRecord | null>(null);
  const [statusFilter, setStatusFilter] = useState<ConsentStatus | 'todos'>('todos');

  const handleConfirmRevoke = (recordId: string, reason: string) => {
    setRecords((prev) =>
      prev.map((r) => r.id === recordId ? { ...r, estado: 'revocado', motivoRevocacion: reason } : r),
    );
    setRevokeTarget(null);
  };

  const activos   = useMemo(() => records.filter((r) => r.estado === 'activo').length,   [records]);
  const revocados = useMemo(() => records.filter((r) => r.estado === 'revocado').length, [records]);

  const filteredRecords = useMemo(
    () => statusFilter === 'todos' ? records : records.filter((r) => r.estado === statusFilter),
    [records, statusFilter],
  );

  const firstName = user?.name.split(' ')[0] ?? '';

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <p className={styles.greeting}>Hola, {firstName}</p>
          <h1 className={styles.title}>Mis consentimientos</h1>
          <p className={styles.subtitle}>
            Aquí puedes consultar y revocar los consentimientos que has otorgado.
          </p>
        </div>
        <div className={styles.stats}>
          <div className={styles.stat}>
            <span className={styles.statValue}>{records.length}</span>
            <span className={styles.statLabel}>Total</span>
          </div>
          <div className={styles.stat}>
            <span className={[styles.statValue, styles.statActivo].join(' ')}>{activos}</span>
            <span className={styles.statLabel}>Activos</span>
          </div>
          <div className={styles.stat}>
            <span className={[styles.statValue, styles.statRevocado].join(' ')}>{revocados}</span>
            <span className={styles.statLabel}>Revocados</span>
          </div>
        </div>
      </div>

      {/* Filtros */}
      {records.length > 0 && (
        <div className={styles.filterRow}>
          {ALL_STATUSES.map(({ value, label }) => (
            <button
              key={value}
              className={[styles.filterPill, statusFilter === value ? styles.filterPillActive : ''].join(' ')}
              onClick={() => setStatusFilter(value)}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      {records.length === 0 ? (
        <div className={styles.empty}>
          <svg className={styles.emptyIcon} width="60" height="60" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.6 1h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.91 8.6a16 16 0 0 0 6 6l.96-.96a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/>
          </svg>
          <p className={styles.emptyTitle}>No tienes consentimientos registrados</p>
          <p className={styles.emptyHint}>Cuando otorgues un consentimiento aparecerá aquí.</p>
        </div>
      ) : filteredRecords.length === 0 ? (
        <div className={styles.empty}>
          <svg className={styles.emptyIcon} width="60" height="60" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            <line x1="8" y1="11" x2="14" y2="11"/>
          </svg>
          <p className={styles.emptyTitle}>Sin resultados</p>
          <p className={styles.emptyHint}>No tienes consentimientos con ese estado.</p>
        </div>
      ) : (
        <div className={styles.cardGrid}>
          {filteredRecords.map((record) => (
            <ConsentCard key={record.id} record={record} onRevoke={setRevokeTarget} />
          ))}
        </div>
      )}

      <div className={styles.legalNote}>
        <p>
          De acuerdo con la <strong>Ley 21.719</strong>, tienes derecho a revocar tu consentimiento en
          cualquier momento. La revocación no afecta el tratamiento realizado antes de la misma.
        </p>
      </div>

      <RevokeModal
        record={revokeTarget}
        onClose={() => setRevokeTarget(null)}
        onConfirm={handleConfirmRevoke}
      />
    </div>
  );
};

export default TitularPortalPage;
