import Modal from './Modal';
import Badge from './Badge';
import Button from './Button';
import type { AgreementResponse } from '../../api/agreementsApi';
import { formatDateLong } from '../../utils/formatters';
import styles from './ConsentDrawer.module.css';

interface ConsentDrawerProps {
  record: AgreementResponse | null;
  onClose: () => void;
}

const ConsentDrawer = ({ record, onClose }: ConsentDrawerProps) => (
  <Modal open={record !== null} onClose={onClose} variant="drawer">
    {record && (
      <div className={styles.drawer}>
        <div className={styles.header}>
          <div>
            <p className={styles.recordId}>{record.id}</p>
            <p className={styles.recordArea}>
              {record.purposes?.[0]?.purposeName ?? '—'}
            </p>
          </div>
          <button className={styles.closeBtn} onClick={onClose} aria-label="Cerrar">✕</button>
        </div>

        <div className={styles.statusBanner}>
          <Badge status={record.status} />
          <span className={styles.statusHint}>
            {record.status === 'ACTIVE'  && 'Consentimiento vigente'}
            {record.status === 'REVOKED' && 'Consentimiento revocado por el titular'}
            {record.status === 'EXPIRED' && 'Consentimiento vencido sin renovar'}
          </span>
        </div>

        <div className={styles.body}>
          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>Consentimiento</h3>
            <dl className={styles.grid}>
              <Field label="Finalidad"    value={record.purposes?.[0]?.purposeName ?? '—'} />
              <Field label="Versión"      value={`v${record.templateVersion}`} />
              <Field label="Otorgamiento" value={formatDateLong(record.createdAt)} />
              <Field label="Expiración"   value={record.expiration ? formatDateLong(record.expiration) : '—'} />
            </dl>
          </section>

          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>Marco legal</h3>
            <dl className={styles.grid}>
              <Field label="Normativa"  value="Ley 21.719 — Protección de datos personales (Chile)" />
              <Field
                label="Base legal"
                value={record.purposes?.[0]?.legalBasisCode ?? 'Consentimiento expreso del titular (Art. 12)'}
              />
            </dl>
          </section>
        </div>

        <div className={styles.footer}>
          <Button variant="ghost" onClick={onClose}>Cerrar</Button>
        </div>
      </div>
    )}
  </Modal>
);

const Field = ({ label, value }: { label: string; value: string }) => (
  <>
    <dt className={styles.fieldLabel}>{label}</dt>
    <dd className={styles.fieldValue}>{value}</dd>
  </>
);

export default ConsentDrawer;
