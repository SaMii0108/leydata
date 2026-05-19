import Button from '../components/common/Button';
import styles from './CompliancePage.module.css';

type CheckStatus  = 'ok' | 'warning' | 'error';
type AlertLevel   = 'high' | 'medium' | 'low';

interface ComplianceCheck {
  id: string;
  category: string;
  requirement: string;
  status: CheckStatus;
  detail: string;
}

interface ComplianceAlert {
  id: string;
  level: AlertLevel;
  title: string;
  description: string;
  action: string;
  affectedCount: number;
}

const SCORE = 87;

const checks: ComplianceCheck[] = [
  { id: 'c01', category: 'Base legal',       requirement: 'Registro de consentimientos activo',                status: 'ok',      detail: 'Sistema de registro operativo con 45.823 entradas.' },
  { id: 'c02', category: 'Base legal',       requirement: 'Política de privacidad publicada',                  status: 'ok',      detail: 'Documento publicado y accesible para los titulares.' },
  { id: 'c03', category: 'Base legal',       requirement: 'Procedimiento de revocación habilitado',            status: 'ok',      detail: 'Los titulares pueden revocar su consentimiento en todo momento.' },
  { id: 'c04', category: 'Base legal',       requirement: 'Finalidades documentadas para cada consentimiento', status: 'ok',      detail: 'Todas las finalidades están registradas en el sistema.' },
  { id: 'c05', category: 'Derechos ARCO',    requirement: 'Canal de solicitudes ARCO operativo',               status: 'ok',      detail: 'Canal habilitado con SLA de 30 días hábiles.' },
  { id: 'c06', category: 'Derechos ARCO',    requirement: 'Registro de solicitudes ARCO',                      status: 'warning', detail: 'Se requiere documentar formalmente el registro de solicitudes.' },
  { id: 'c07', category: 'Seguridad',        requirement: 'Cifrado de datos personales en reposo',             status: 'ok',      detail: 'Datos cifrados con AES-256.' },
  { id: 'c08', category: 'Seguridad',        requirement: 'Registro de accesos a datos personales',            status: 'warning', detail: 'Audit trail parcial — falta cobertura en módulo de exportación.' },
  { id: 'c09', category: 'Seguridad',        requirement: 'Plan de respuesta ante brechas de seguridad',       status: 'error',   detail: 'Documento no encontrado. Se requiere elaborar y aprobar el plan.' },
  { id: 'c10', category: 'Transparencia',    requirement: 'Avisos de privacidad al momento de captura',        status: 'ok',      detail: 'Avisos integrados en todos los formularios de consentimiento.' },
  { id: 'c11', category: 'Transparencia',    requirement: 'Notificación al titular ante cambio de finalidad',  status: 'ok',      detail: 'Notificaciones automáticas configuradas.' },
  { id: 'c12', category: 'Transferencias',   requirement: 'Contratos con encargados de tratamiento vigentes',  status: 'warning', detail: '2 contratos próximos a vencer en los próximos 60 días.' },
];

const alerts: ComplianceAlert[] = [
  {
    id: 'a01',
    level: 'high',
    title: 'Plan de respuesta ante brechas ausente',
    description: 'La Ley 21.719 exige un protocolo formal de respuesta ante incidentes de seguridad. No se encontró documentación vigente.',
    action: 'Elaborar y aprobar el plan de respuesta',
    affectedCount: 0,
  },
  {
    id: 'a02',
    level: 'high',
    title: 'Consentimientos próximos a vencer',
    description: '89 registros expirarán en los próximos 30 días. Se requiere renovación activa o notificación a los titulares.',
    action: 'Revisar registros expirados',
    affectedCount: 89,
  },
  {
    id: 'a03',
    level: 'medium',
    title: 'Contratos de encargados por renovar',
    description: '2 contratos con encargados de tratamiento de datos vencen en los próximos 60 días.',
    action: 'Gestionar renovación de contratos',
    affectedCount: 2,
  },
  {
    id: 'a04',
    level: 'medium',
    title: 'Registro ARCO incompleto',
    description: 'Las solicitudes de derechos ARCO recibidas no están siendo documentadas formalmente en el sistema.',
    action: 'Habilitar módulo de registro ARCO',
    affectedCount: 0,
  },
  {
    id: 'a05',
    level: 'low',
    title: 'Audit trail — cobertura parcial',
    description: 'El registro de auditoría no cubre el módulo de exportación de datos. Se recomienda ampliar la cobertura.',
    action: 'Completar configuración del audit trail',
    affectedCount: 0,
  },
];

const ScoreGauge = ({ score }: { score: number }) => {
  const r     = 52;
  const circ  = 2 * Math.PI * r;
  const dash  = (score / 100) * circ;
  const color = score >= 80 ? 'var(--color-success)' : score >= 60 ? 'var(--color-warning)' : 'var(--color-danger)';
  const label = score >= 80 ? 'Conforme' : score >= 60 ? 'En revisión' : 'En incumplimiento';

  return (
    <div className={styles.gaugeWrap}>
      <svg width="140" height="140" viewBox="0 0 140 140">
        <circle cx="70" cy="70" r={r} fill="none" stroke="var(--color-border)" strokeWidth="10" />
        <circle
          cx="70" cy="70" r={r} fill="none"
          stroke={color} strokeWidth="10"
          strokeDasharray={`${dash} ${circ}`}
          strokeLinecap="round"
          transform="rotate(-90 70 70)"
          style={{ transition: 'stroke-dasharray 0.6s ease' }}
        />
        <text x="70" y="65" textAnchor="middle" fontSize="26" fontWeight="700" fill="var(--color-text-primary)">{score}</text>
        <text x="70" y="82" textAnchor="middle" fontSize="11" fill="var(--color-text-secondary)">/100</text>
      </svg>
      <div className={styles.gaugeLabel}>
        <span className={styles.gaugeBadge} style={{ background: color + '22', color }}>{label}</span>
        <p className={styles.gaugeHint}>Puntuación de cumplimiento</p>
      </div>
    </div>
  );
};

const statusIcon: Record<CheckStatus, string>  = { ok: '✓', warning: '⚠', error: '✕' };
const levelLabel: Record<AlertLevel, string>   = { high: 'Alto', medium: 'Medio', low: 'Bajo' };

const CompliancePage = () => {
  const byCategory = checks.reduce<Record<string, ComplianceCheck[]>>((acc, c) => {
    (acc[c.category] ||= []).push(c);
    return acc;
  }, {});

  const okCount      = checks.filter((c) => c.status === 'ok').length;
  const warningCount = checks.filter((c) => c.status === 'warning').length;
  const errorCount   = checks.filter((c) => c.status === 'error').length;

  return (
    <div className={styles.page}>
      {/* Page header */}
      <div className={styles.pageHeader}>
        <div>
          <h2 className={styles.title}>Cumplimiento Legal</h2>
          <p className={styles.subtitle}>Estado de conformidad con la Ley 21.719 — Protección de datos personales</p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => console.warn('Exportar informe: pendiente integración backend')}>
          Exportar informe
        </Button>
      </div>

      {/* Top row: score + summary */}
      <div className={styles.topRow}>
        <div className={styles.scoreCard}>
          <ScoreGauge score={SCORE} />
          <div className={styles.scoreSummary}>
            <div className={styles.scoreItem}>
              <span className={[styles.scoreCount, styles.ok].join(' ')}>{okCount}</span>
              <span className={styles.scoreItemLabel}>Cumplidos</span>
            </div>
            <div className={styles.scoreItem}>
              <span className={[styles.scoreCount, styles.warning].join(' ')}>{warningCount}</span>
              <span className={styles.scoreItemLabel}>Con observaciones</span>
            </div>
            <div className={styles.scoreItem}>
              <span className={[styles.scoreCount, styles.error].join(' ')}>{errorCount}</span>
              <span className={styles.scoreItemLabel}>Incumplidos</span>
            </div>
          </div>
        </div>

        {/* Active alerts summary */}
        <div className={styles.alertsCard}>
          <h3 className={styles.cardTitle}>Alertas activas</h3>
          <div className={styles.alertsList}>
            {alerts.map((alert) => (
              <div key={alert.id} className={[styles.alertItem, styles[`alert_${alert.level}`]].join(' ')}>
                <div className={styles.alertTop}>
                  <span className={[styles.alertBadge, styles[`badge_${alert.level}`]].join(' ')}>
                    {levelLabel[alert.level]}
                  </span>
                  {alert.affectedCount > 0 && (
                    <span className={styles.alertCount}>{alert.affectedCount} registros</span>
                  )}
                </div>
                <p className={styles.alertTitle}>{alert.title}</p>
                <p className={styles.alertDesc}>{alert.description}</p>
                <Button variant="ghost" size="sm">{alert.action} →</Button>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Checklist */}
      <section className={styles.checklistSection}>
        <h3 className={styles.sectionTitle}>Requisitos Ley 21.719</h3>
        <div className={styles.checklistGrid}>
          {Object.entries(byCategory).map(([category, items]) => (
            <div key={category} className={styles.categoryBlock}>
              <p className={styles.categoryLabel}>{category}</p>
              <div className={styles.checkItems}>
                {items.map((check) => (
                  <div key={check.id} className={[styles.checkRow, styles[`check_${check.status}`]].join(' ')}>
                    <span className={[styles.checkIcon, styles[`icon_${check.status}`]].join(' ')}>
                      {statusIcon[check.status]}
                    </span>
                    <div className={styles.checkContent}>
                      <p className={styles.checkReq}>{check.requirement}</p>
                      <p className={styles.checkDetail}>{check.detail}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
};

export default CompliancePage;
