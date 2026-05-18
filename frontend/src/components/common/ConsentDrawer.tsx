import Modal from './Modal';
import Badge from './Badge';
import Button from './Button';
import type { ConsentRecord } from '../../utils/mockData';
import { formatDateLong } from '../../utils/formatters';
import styles from './ConsentDrawer.module.css';

interface ConsentDrawerProps {
  record: ConsentRecord | null;
  onClose: () => void;
}

const ConsentDrawer = ({ record, onClose }: ConsentDrawerProps) => (
  <Modal open={record !== null} onClose={onClose} variant="drawer">
    {record && (
      <div className={styles.drawer}>
        <div className={styles.header}>
          <div>
            <p className={styles.recordId}>{record.id}</p>
            <p className={styles.recordArea}>{record.area}</p>
          </div>
          <button className={styles.closeBtn} onClick={onClose} aria-label="Cerrar">✕</button>
        </div>

        <div className={styles.statusBanner}>
          <Badge status={record.estado} />
          <span className={styles.statusHint}>
            {record.estado === 'activo'    && 'Consentimiento vigente'}
            {record.estado === 'revocado'  && 'Consentimiento revocado por el titular'}
            {record.estado === 'pendiente' && 'Pendiente de validación'}
            {record.estado === 'expirado'  && 'Consentimiento vencido sin renovar'}
          </span>
        </div>

        <div className={styles.body}>
          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>Consentimiento</h3>
            <dl className={styles.grid}>
              <Field label="Área"          value={record.area} />
              <Field label="Finalidad"     value={record.finalidad} />
              <Field label="Otorgamiento"  value={formatDateLong(record.fechaOtorgamiento)} />
              <Field label="Expiración"    value={formatDateLong(record.fechaExpiracion)} />
            </dl>
          </section>

          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>Marco legal</h3>
            <dl className={styles.grid}>
              <Field label="Normativa"  value="Ley 21.719 — Protección de datos personales (Chile)" />
              <Field label="Base legal" value="Consentimiento expreso del titular (Art. 12)" />
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
