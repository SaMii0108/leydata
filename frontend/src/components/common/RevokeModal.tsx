import { useState } from 'react';
import Modal from './Modal';
import Button from './Button';
import type { ConsentRecord } from '../../utils/mockData';
import styles from './RevokeModal.module.css';

interface RevokeModalProps {
  record: ConsentRecord | null;
  onClose: () => void;
  onConfirm: (recordId: string, reason: string) => void;
}

const RevokeModal = ({ record, onClose, onConfirm }: RevokeModalProps) => {
  const [reason, setReason] = useState('');

  const handleConfirm = () => {
    if (!record || !reason.trim()) return;
    onConfirm(record.id, reason.trim());
    setReason('');
  };

  const handleClose = () => {
    setReason('');
    onClose();
  };

  return (
    <Modal open={record !== null} onClose={handleClose} variant="center">
      {record && (
        <div className={styles.modal}>
          {/* Header */}
          <div className={styles.header}>
            <div className={styles.warningIcon}>⚠</div>
            <div>
              <h2 className={styles.title}>Revocar consentimiento</h2>
              <p className={styles.subtitle}>Esta acción no se puede deshacer.</p>
            </div>
          </div>

          {/* Info */}
          <div className={styles.infoBox}>
            <p className={styles.infoLabel}>ID</p>
            <p className={styles.infoValue}>{record.id}</p>
            <p className={styles.infoLabel}>Área</p>
            <p className={styles.infoValue}>{record.area}</p>
            <p className={styles.infoLabel}>Finalidad</p>
            <p className={styles.infoValue}>{record.finalidad}</p>
          </div>

          {/* Reason field */}
          <div className={styles.field}>
            <label htmlFor="revoke-reason" className={styles.label}>
              Razón de revocación <span className={styles.required}>*</span>
            </label>
            <textarea
              id="revoke-reason"
              className={styles.textarea}
              placeholder="Describe el motivo de la revocación..."
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
            />
            <p className={styles.charCount}>{reason.length}/500</p>
          </div>

          {/* Actions */}
          <div className={styles.actions}>
            <Button variant="ghost" onClick={handleClose}>Cancelar</Button>
            <Button
              variant="danger"
              onClick={handleConfirm}
              disabled={!reason.trim()}
            >
              Confirmar revocación
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
};

export default RevokeModal;
