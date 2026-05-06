import type { ConsentStatus } from '../../utils/mockData';
import styles from './Badge.module.css';

interface BadgeProps {
  status: ConsentStatus;
}

const labels: Record<ConsentStatus, string> = {
  activo:    'Activo',
  revocado:  'Revocado',
  pendiente: 'Pendiente',
  expirado:  'Expirado',
};

const Badge = ({ status }: BadgeProps) => (
  <span className={[styles.badge, styles[status]].join(' ')}>
    <span className={styles.dot} />
    {labels[status]}
  </span>
);

export default Badge;
