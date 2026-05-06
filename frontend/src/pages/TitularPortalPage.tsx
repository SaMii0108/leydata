import { useState, useMemo } from 'react';
import { consentRecords as initialRecords } from '../utils/mockData';
import type { ConsentRecord } from '../utils/mockData';
import { useAuth } from '../features/auth/AuthContext';
import RevokeModal from '../components/common/RevokeModal';
import Badge from '../components/common/Badge';
import styles from './TitularPortalPage.module.css';

const TitularPortalPage = () => {
  const { user } = useAuth();
  const [records, setRecords] = useState<ConsentRecord[]>(
    initialRecords.filter((r) => r.titularId === user?.id),
  );
  const [revokeTarget, setRevokeTarget] = useState<ConsentRecord | null>(null);

  const handleConfirmRevoke = (recordId: string) => {
    setRecords((prev) =>
      prev.map((r) => r.id === recordId ? { ...r, estado: 'revocado' } : r),
    );
    setRevokeTarget(null);
  };

  const activos   = useMemo(() => records.filter((r) => r.estado === 'activo').length, [records]);
  const revocados = useMemo(() => records.filter((r) => r.estado === 'revocado').length, [records]);

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
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

      {records.length === 0 ? (
        <div className={styles.empty}>
          <p className={styles.emptyTitle}>No tienes consentimientos registrados</p>
          <p className={styles.emptyHint}>Cuando otorgues un consentimiento aparecerá aquí.</p>
        </div>
      ) : (
        <div className={styles.cardGrid}>
          {records.map((record) => (
            <div key={record.id} className={[styles.card, styles[`card_${record.estado}`]].join(' ')}>
              <div className={styles.cardHeader}>
                <span className={styles.cardId}>{record.id}</span>
                <Badge status={record.estado} />
              </div>

              <div className={styles.cardBody}>
                <div className={styles.field}>
                  <span className={styles.fieldKey}>Área</span>
                  <span className={styles.fieldVal}>{record.area}</span>
                </div>
                <div className={styles.field}>
                  <span className={styles.fieldKey}>Finalidad</span>
                  <span className={styles.fieldVal}>{record.finalidad}</span>
                </div>
                <div className={styles.field}>
                  <span className={styles.fieldKey}>Otorgamiento</span>
                  <span className={styles.fieldVal}>{formatDate(record.fechaOtorgamiento)}</span>
                </div>
                <div className={styles.field}>
                  <span className={styles.fieldKey}>Expiración</span>
                  <span className={styles.fieldVal}>{formatDate(record.fechaExpiracion)}</span>
                </div>
              </div>

              <div className={styles.cardFooter}>
                {record.estado === 'activo' && (
                  <button
                    className={styles.revokeBtn}
                    onClick={() => setRevokeTarget(record)}
                  >
                    Revocar consentimiento
                  </button>
                )}
                {record.estado === 'revocado' && (
                  <p className={styles.revokedNote}>Revocado — ya no se trata tu información.</p>
                )}
                {record.estado === 'expirado' && (
                  <p className={styles.expiredNote}>Este consentimiento ha vencido.</p>
                )}
                {record.estado === 'pendiente' && (
                  <p className={styles.pendingNote}>Pendiente de validación por el responsable.</p>
                )}
              </div>
            </div>
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

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });

export default TitularPortalPage;
