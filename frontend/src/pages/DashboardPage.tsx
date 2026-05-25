import { useMemo } from 'react';
import Badge from '../components/common/Badge';
import Button from '../components/common/Button';
import { summaryCards, chartData, consentRecords } from '../utils/mockData';
import type { SummaryCard } from '../utils/mockData';
import { usePermissions } from '../features/auth/usePermissions';
import { useNavigate } from 'react-router-dom';
import { formatDate } from '../utils/formatters';
import styles from './DashboardPage.module.css';

const Sparkline = ({ values, trend }: { values: number[]; trend: SummaryCard['trend'] }) => {
  const w = 80;
  const h = 32;
  const max = Math.max(...values);
  const min = Math.min(...values);
  const range = max - min || 1;
  const pts = values.map((v, i) => {
    const x = (i / (values.length - 1)) * w;
    const y = h - ((v - min) / range) * h;
    return `${x},${y}`;
  }).join(' ');
  const color = trend === 'up' ? 'var(--color-primary)' : 'var(--color-danger)';
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} fill="none">
      <polyline points={pts} stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
};

const ConsentChart = () => {
  const maxVal = Math.max(...chartData.flatMap((d) => [d.granted, d.rejected, d.revoked]));
  const H = 180;
  const barW = 8;
  const gap = 4;
  const groupW = barW * 3 + gap * 2 + 10;
  const totalW = chartData.length * groupW;

  return (
    <div className={styles.chartWrap}>
      <div className={styles.chartInner}>
        {/* Y axis labels */}
        <div className={styles.yAxis}>
          {[maxVal, Math.round(maxVal * 0.75), Math.round(maxVal * 0.5), Math.round(maxVal * 0.25), 0].map((v) => (
            <span key={v}>{v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v}</span>
          ))}
        </div>

        {/* Chart */}
        <div className={styles.chartScroll}>
          <svg width={totalW} height={H} style={{ overflow: 'visible' }}>
            {/* Grid lines */}
            {[0, 0.25, 0.5, 0.75, 1].map((p) => (
              <line key={p} x1={0} y1={H * (1 - p)} x2={totalW} y2={H * (1 - p)}
                stroke="var(--color-border)" strokeWidth="1" strokeDasharray="4 4"/>
            ))}

            {/* Bars */}
            {chartData.map((day, i) => {
              const x = i * groupW;
              const gH = (day.granted / maxVal) * H;
              const rH = (day.rejected / maxVal) * H;
              const vH = (day.revoked / maxVal) * H;
              return (
                <g key={day.date}>
                  <rect x={x}             y={H - gH} width={barW} height={gH} rx="2" fill="var(--color-chart-granted)" opacity="0.9"/>
                  <rect x={x + barW + gap} y={H - rH} width={barW} height={rH} rx="2" fill="var(--color-chart-rejected)" opacity="0.9"/>
                  <rect x={x + (barW + gap) * 2} y={H - vH} width={barW} height={vH} rx="2" fill="var(--color-chart-revoked)" opacity="0.9"/>
                </g>
              );
            })}
          </svg>

          {/* X axis dates */}
          <div className={styles.xAxis} style={{ width: totalW }}>
            {chartData.map((d) => (
              <span key={d.date} style={{ width: groupW }}>{d.date}</span>
            ))}
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className={styles.legend}>
        <span className={styles.legendItem}><i style={{ background: 'var(--color-chart-granted)' }} />Otorgados</span>
        <span className={styles.legendItem}><i style={{ background: 'var(--color-chart-rejected)' }} />Rechazados</span>
        <span className={styles.legendItem}><i style={{ background: 'var(--color-chart-revoked)' }} />Revocados</span>
      </div>
    </div>
  );
};

const CARD_COLORS = [
  { color: '#4361ee', bg: '#eef1fd' }, // Total — azul
  { color: '#16a34a', bg: '#dcfce7' }, // Otorgados — verde
  { color: '#dc2626', bg: '#fee2e2' }, // Rechazados — rojo
  { color: '#d97706', bg: '#fef3c7' }, // Revocados — naranja
];

const CARD_ICONS = [
  // Total — lista
  <svg key="total" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
    <polyline points="14 2 14 8 20 8"/>
    <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
  </svg>,
  // Otorgados — check
  <svg key="otorgados" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
    <polyline points="22 4 12 14.01 9 11.01"/>
  </svg>,
  // Rechazados — X circle
  <svg key="rechazados" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"/>
    <line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
  </svg>,
  // Revocados — ban
  <svg key="revocados" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"/>
    <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
  </svg>,
];

const CardIcon = ({ index }: { index: number }) => {
  const { color, bg } = CARD_COLORS[index] ?? CARD_COLORS[0];
  return (
    <div className={styles.cardIcon} style={{ background: bg, color }}>
      {CARD_ICONS[index] ?? CARD_ICONS[0]}
    </div>
  );
};

const DashboardPage = () => {
  const { canCreate, isJefeDominio, userDomains } = usePermissions();
  const navigate = useNavigate();

  const domainName = userDomains[0] ?? null;

  /* ── Registros filtrados por dominio (JEFE_DOMINIO) ─────────────────────── */
  const visibleRecords = useMemo(
    () => isJefeDominio && domainName
      ? consentRecords.filter((r) => r.area === domainName)
      : consentRecords,
    [isJefeDominio, domainName],
  );

  /* ── Cards calculadas para JEFE_DOMINIO ─────────────────────────────────── */
  const domainCards: SummaryCard[] = useMemo(() => {
    if (!isJefeDominio) return summaryCards;
    const total    = visibleRecords.length;
    const activos  = visibleRecords.filter((r) => r.estado === 'activo').length;
    const pendien  = visibleRecords.filter((r) => r.estado === 'pendiente').length;
    const revocados = visibleRecords.filter((r) => r.estado === 'revocado').length;
    return [
      { label: 'Total consentimientos', value: total,    delta: '—', deltaLabel: 'en tu dominio', trend: 'neutral', sparkline: [total, total, total, total, total] },
      { label: 'Activos',               value: activos,  delta: '—', deltaLabel: 'activos',        trend: 'up',      sparkline: [activos, activos, activos, activos, activos] },
      { label: 'Pendientes',            value: pendien,  delta: '—', deltaLabel: 'pendientes',     trend: 'neutral', sparkline: [pendien, pendien, pendien, pendien, pendien] },
      { label: 'Revocados',             value: revocados,delta: '—', deltaLabel: 'revocados',      trend: 'down',    sparkline: [revocados, revocados, revocados, revocados, revocados] },
    ];
  }, [isJefeDominio, visibleRecords]);

  const cards = isJefeDominio ? domainCards : summaryCards;

  return (
  <div className={styles.page}>
    <div className={styles.pageHeader}>
      <div>
        <h2 className={styles.title}>
          {isJefeDominio && domainName
            ? `Métricas · ${domainName}`
            : 'Métricas Generales'}
        </h2>
        <p className={styles.subtitle}>
          {isJefeDominio
            ? `Consentimientos activos en el dominio ${domainName}`
            : 'Panel de gestión de consentimientos en tiempo real'}
        </p>
      </div>
      {canCreate && (
        <Button variant="primary" onClick={() => navigate('/consentimientos/nuevo')}>
          + Nuevo consentimiento
        </Button>
      )}
    </div>

    {/* Summary cards */}
    <section className={styles.cardsGrid}>
      {cards.map((card, i) => (
        <div key={card.label} className={styles.card}>
          <div className={styles.cardTop}>
            <p className={styles.cardLabel}>{card.label}</p>
            <CardIcon index={i} />
          </div>
          <p className={styles.cardValue}>{card.value.toLocaleString('es-CL')}</p>
          <div className={styles.cardBottom}>
            {card.trend !== 'neutral' ? (
              <>
                <span className={[
                  styles.cardDelta,
                  card.trend === 'up' ? styles.deltaUp : styles.deltaDown,
                ].join(' ')}>
                  {card.trend === 'up' ? '↗' : '↘'} {card.delta} {card.deltaLabel}
                </span>
                <Sparkline values={card.sparkline} trend={card.trend} />
              </>
            ) : (
              <span className={styles.cardDeltaNeutral}>{card.deltaLabel}</span>
            )}
          </div>
        </div>
      ))}
    </section>

    {/* Consent Trends chart — solo ADMIN y DPO */}
    {!isJefeDominio && (
      <section className={styles.chartSection}>
        <div className={styles.chartHeader}>
          <div>
            <h3 className={styles.chartTitle}>Tendencias de Consentimiento – Últimos 14 días</h3>
            <p className={styles.chartSubtitle}>Actividad diaria por tipo de consentimiento</p>
          </div>
        </div>
        <ConsentChart />
      </section>
    )}

    {/* Recent records */}
    <section className={styles.tableSection}>
      <div className={styles.tableHeader}>
        <h3 className={styles.tableTitle}>
          {isJefeDominio ? `Consentimientos de ${domainName}` : 'Registros recientes'}
        </h3>
        <Button variant="ghost" size="sm" onClick={() => navigate('/consentimientos')}>
          Ver todos →
        </Button>
      </div>
      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>ID</th>
              {!isJefeDominio && <th>Área</th>}
              <th>Finalidad</th>
              <th>Estado</th>
              <th>Fecha</th>
            </tr>
          </thead>
          <tbody>
            {visibleRecords.slice(0, 5).map((r) => (
              <tr key={r.id}>
                <td className={styles.cellId}>{r.id}</td>
                {!isJefeDominio && <td className={styles.cellMuted}>{r.area}</td>}
                <td className={styles.cellMuted}>{r.finalidad}</td>
                <td><Badge status={r.estado} /></td>
                <td className={styles.cellMuted}>{formatDate(r.fechaOtorgamiento)}</td>
              </tr>
            ))}
            {visibleRecords.length === 0 && (
              <tr>
                <td colSpan={isJefeDominio ? 4 : 5} className={styles.cellMuted} style={{ textAlign: 'center', padding: '32px 0' }}>
                  No hay registros para este dominio.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  </div>
  );
};

export default DashboardPage;
