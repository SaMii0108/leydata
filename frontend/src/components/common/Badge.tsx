import styles from './Badge.module.css';

export type BadgeStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED';

interface BadgeProps {
  status: BadgeStatus;
}

const labels: Record<BadgeStatus, string> = {
  ACTIVE:  'Activo',
  REVOKED: 'Revocado',
  EXPIRED: 'Expirado',
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

const IconMinus = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round">
    <line x1="5" y1="12" x2="19" y2="12"/>
  </svg>
);

const StatusIcon = ({ status }: { status: BadgeStatus }) => {
  if (status === 'ACTIVE')  return <IconCheck />;
  if (status === 'REVOKED') return <IconX />;
  return <IconMinus />;
};

const Badge = ({ status }: BadgeProps) => (
  <span className={[styles.badge, styles[status]].join(' ')}>
    <span className={styles.icon}><StatusIcon status={status} /></span>
    {labels[status]}
  </span>
);

export default Badge;
