import type { AgreementResponse } from '../../api/agreementsApi';
import { formatDate } from '../../utils/formatters';
import Badge from './Badge';
import styles from './ConsentCard.module.css';

interface ConsentCardProps {
  record: AgreementResponse;
  onRevoke: (record: AgreementResponse) => void;
}

const ConsentCard = ({ record, onRevoke }: ConsentCardProps) => {
  const firstPurpose = record.purposes?.[0];

  return (
    <div className={[styles.card, styles[`card_${record.status}`]].join(' ')}>
      <div className={styles.cardHeader}>
        <span className={styles.cardId}>{record.id.slice(0, 8)}…</span>
        <Badge status={record.status} />
      </div>

      <div className={styles.cardBody}>
        <Row label="Finalidad"    value={firstPurpose?.purposeName ?? '—'} />
        <Row label="Versión"      value={`v${record.templateVersion}`} />
        <Row label="Otorgamiento" value={formatDate(record.createdAt)} />
        <Row label="Expiración"   value={record.expiration ? formatDate(record.expiration) : '—'} />
      </div>

      <div className={styles.cardFooter}>
        {record.status === 'ACTIVE' && (
          <button className={styles.revokeBtn} onClick={() => onRevoke(record)}>
            Revocar consentimiento
          </button>
        )}
        {record.status === 'REVOKED' && (
          <p className={styles.note}>Revocado — ya no se trata tu información.</p>
        )}
        {record.status === 'EXPIRED' && (
          <p className={styles.note}>Este consentimiento ha vencido.</p>
        )}
      </div>
    </div>
  );
};

const Row = ({ label, value }: { label: string; value: string }) => (
  <div className={styles.field}>
    <span className={styles.fieldKey}>{label}</span>
    <span className={styles.fieldVal}>{value}</span>
  </div>
);

export default ConsentCard;
