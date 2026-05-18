import type { ConsentRecord } from '../../utils/mockData';
import { formatDate } from '../../utils/formatters';
import Badge from './Badge';
import styles from './ConsentCard.module.css';

interface ConsentCardProps {
  record: ConsentRecord;
  onRevoke: (record: ConsentRecord) => void;
}

const ConsentCard = ({ record, onRevoke }: ConsentCardProps) => (
  <div className={[styles.card, styles[`card_${record.estado}`]].join(' ')}>
    <div className={styles.cardHeader}>
      <span className={styles.cardId}>{record.id}</span>
      <Badge status={record.estado} />
    </div>

    <div className={styles.cardBody}>
      <Row label="Área"         value={record.area} />
      <Row label="Finalidad"    value={record.finalidad} />
      <Row label="Otorgamiento" value={formatDate(record.fechaOtorgamiento)} />
      <Row label="Expiración"   value={formatDate(record.fechaExpiracion)} />
    </div>

    <div className={styles.cardFooter}>
      {record.estado === 'activo' && (
        <button className={styles.revokeBtn} onClick={() => onRevoke(record)}>
          Revocar consentimiento
        </button>
      )}
      {record.estado === 'revocado' && (
        <p className={styles.note}>Revocado — ya no se trata tu información.</p>
      )}
      {record.estado === 'expirado' && (
        <p className={styles.note}>Este consentimiento ha vencido.</p>
      )}
      {record.estado === 'pendiente' && (
        <p className={styles.note}>Pendiente de validación por el responsable.</p>
      )}
    </div>
  </div>
);

const Row = ({ label, value }: { label: string; value: string }) => (
  <div className={styles.field}>
    <span className={styles.fieldKey}>{label}</span>
    <span className={styles.fieldVal}>{value}</span>
  </div>
);

export default ConsentCard;
