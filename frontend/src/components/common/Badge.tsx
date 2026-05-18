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

const IconCheck = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);

const IconX = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
  </svg>
);

const IconClock = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
  </svg>
);

const IconMinus = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round">
    <line x1="5" y1="12" x2="19" y2="12"/>
  </svg>
);

const StatusIcon = ({ status }: { status: ConsentStatus }) => {
  if (status === 'activo')    return <IconCheck />;
  if (status === 'revocado')  return <IconX />;
  if (status === 'pendiente') return <IconClock />;
  return <IconMinus />;
};

const Badge = ({ status }: BadgeProps) => (
  <span className={[styles.badge, styles[status]].join(' ')}>
    <span className={styles.icon}><StatusIcon status={status} /></span>
    {labels[status]}
  </span>
);

export default Badge;
