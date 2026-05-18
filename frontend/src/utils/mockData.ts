export type ConsentStatus = 'activo' | 'revocado' | 'pendiente' | 'expirado';

// ── Audit Log ──────────────────────────────────────────────────────────────
export type AuditAction = 'granted' | 'revoked' | 'updated' | 'viewed' | 'exported' | 'deleted';

export interface AuditEvent {
  id: string;
  action: AuditAction;
  description: string;
  actor: string;
  actorEmail: string;
  consentId: string;
  ipAddress: string;
  timestamp: string;
}

export const auditEvents: AuditEvent[] = [
  { id: 'EVT-001', action: 'granted',  description: 'Consentimiento otorgado para fines de marketing',          actor: 'María José Fuentes',  actorEmail: 'mjfuentes@email.com',   consentId: 'C-0001', ipAddress: '192.168.1.45',  timestamp: '2026-05-06T09:14:22Z' },
  { id: 'EVT-002', action: 'viewed',   description: 'Registro de consentimiento consultado por administrador',   actor: 'Aurora González',     actorEmail: 'auroragc21@gmail.com',  consentId: 'C-0003', ipAddress: '10.0.0.12',     timestamp: '2026-05-06T08:52:10Z' },
  { id: 'EVT-003', action: 'revoked',  description: 'Consentimiento revocado a solicitud del titular',          actor: 'Carlos Andrés Pérez', actorEmail: 'caperez@email.com',     consentId: 'C-0002', ipAddress: '203.0.113.77',  timestamp: '2026-05-05T17:30:05Z' },
  { id: 'EVT-004', action: 'exported', description: 'Registros de consentimiento exportados a CSV',             actor: 'Aurora González',     actorEmail: 'auroragc21@gmail.com',  consentId: '—',      ipAddress: '10.0.0.12',     timestamp: '2026-05-05T14:05:33Z' },
  { id: 'EVT-005', action: 'updated',  description: 'Fecha de expiración del consentimiento extendida 12 meses', actor: 'Aurora González',    actorEmail: 'auroragc21@gmail.com',  consentId: 'C-0004', ipAddress: '10.0.0.12',     timestamp: '2026-05-05T11:20:48Z' },
  { id: 'EVT-006', action: 'granted',  description: 'Consentimiento otorgado para transferencia de datos a terceros', actor: 'Valentina Rojas', actorEmail: 'vrojas@email.com',    consentId: 'C-0003', ipAddress: '172.16.0.8',    timestamp: '2026-05-04T16:44:19Z' },
  { id: 'EVT-007', action: 'viewed',   description: 'Registro de consentimiento consultado por administrador',   actor: 'Aurora González',     actorEmail: 'auroragc21@gmail.com',  consentId: 'C-0007', ipAddress: '10.0.0.12',     timestamp: '2026-05-04T13:10:02Z' },
  { id: 'EVT-008', action: 'revoked',  description: 'Consentimiento revocado — finalidad ya no aplicable',      actor: 'Francisca Gómez',     actorEmail: 'fgomez@email.com',      consentId: 'C-0007', ipAddress: '198.51.100.3',  timestamp: '2026-05-04T09:55:41Z' },
  { id: 'EVT-009', action: 'updated',  description: 'Finalidad del consentimiento actualizada de marketing a análisis', actor: 'Aurora González', actorEmail: 'auroragc21@gmail.com', consentId: 'C-0006', ipAddress: '10.0.0.12',  timestamp: '2026-05-03T15:22:30Z' },
  { id: 'EVT-010', action: 'granted',  description: 'Consentimiento otorgado para investigación académica',     actor: 'Camila Sepúlveda',    actorEmail: 'csepulveda@email.com',  consentId: 'C-0005', ipAddress: '192.168.2.101', timestamp: '2026-05-03T10:08:55Z' },
  { id: 'EVT-011', action: 'exported', description: 'Registro de auditoría exportado para revisión regulatoria', actor: 'Aurora González',    actorEmail: 'auroragc21@gmail.com',  consentId: '—',      ipAddress: '10.0.0.12',     timestamp: '2026-05-02T17:00:00Z' },
  { id: 'EVT-012', action: 'deleted',  description: 'Registro de consentimiento expirado eliminado definitivamente', actor: 'Sistema',         actorEmail: 'system@leydata.cl',     consentId: 'C-0008', ipAddress: '127.0.0.1',     timestamp: '2026-05-01T00:00:00Z' },
];

export interface ConsentRecord {
  id: string;
  titularId: string;
  area: string;
  finalidad: string;
  estado: ConsentStatus;
  fechaOtorgamiento: string;
  fechaExpiracion: string;
  motivoRevocacion?: string;
}

export interface SummaryCard {
  label: string;
  value: number;
  delta: string;
  deltaLabel: string;
  trend: 'up' | 'down' | 'neutral';
  sparkline: number[];
}

export interface ChartDay {
  date: string;
  granted: number;
  rejected: number;
  revoked: number;
}

export const summaryCards: SummaryCard[] = [
  {
    label: 'Total consentimientos',
    value: 45_823,
    delta: '12,5%',
    deltaLabel: 'vs mes anterior',
    trend: 'up',
    sparkline: [310, 340, 280, 390, 420, 380, 450, 490, 510, 540],
  },
  {
    label: 'Otorgados',
    value: 38_412,
    delta: '8,3%',
    deltaLabel: 'vs mes anterior',
    trend: 'up',
    sparkline: [260, 290, 240, 330, 360, 320, 390, 420, 440, 460],
  },
  {
    label: 'Rechazados',
    value: 4_217,
    delta: '3,1%',
    deltaLabel: 'vs mes anterior',
    trend: 'down',
    sparkline: [48, 55, 42, 60, 52, 58, 50, 45, 48, 44],
  },
  {
    label: 'Revocados',
    value: 3_194,
    delta: '1,8%',
    deltaLabel: 'vs mes anterior',
    trend: 'down',
    sparkline: [36, 40, 34, 45, 38, 42, 35, 32, 34, 30],
  },
];

// 14 días de datos de tendencia
export const chartData: ChartDay[] = [
  { date: '23 abr', granted: 820, rejected: 95,  revoked: 42 },
  { date: '24 abr', granted: 950, rejected: 110, revoked: 55 },
  { date: '25 abr', granted: 780, rejected: 88,  revoked: 38 },
  { date: '26 abr', granted: 1050, rejected: 130, revoked: 60 },
  { date: '27 abr', granted: 890, rejected: 102, revoked: 48 },
  { date: '28 abr', granted: 720, rejected: 78,  revoked: 35 },
  { date: '29 abr', granted: 640, rejected: 70,  revoked: 30 },
  { date: '30 abr', granted: 980, rejected: 115, revoked: 52 },
  { date: '1 may',  granted: 1100, rejected: 140, revoked: 65 },
  { date: '2 may',  granted: 1050, rejected: 125, revoked: 58 },
  { date: '3 may',  granted: 920,  rejected: 108, revoked: 50 },
  { date: '4 may',  granted: 1180, rejected: 155, revoked: 70 },
  { date: '5 may',  granted: 1050, rejected: 130, revoked: 62 },
  { date: '6 may',  granted: 980,  rejected: 118, revoked: 55 },
];

export const consentRecords: ConsentRecord[] = [
  { id: 'C-0001', titularId: 't1', area: 'Marketing',        finalidad: 'Marketing directo',          estado: 'activo',    fechaOtorgamiento: '2025-11-03', fechaExpiracion: '2026-11-03' },
  { id: 'C-0002', titularId: 't2', area: 'Tecnología',        finalidad: 'Análisis de datos internos', estado: 'revocado',  fechaOtorgamiento: '2025-08-15', fechaExpiracion: '2026-08-15' },
  { id: 'C-0003', titularId: 't3', area: 'Legal',             finalidad: 'Transferencia a terceros',   estado: 'pendiente', fechaOtorgamiento: '2026-04-28', fechaExpiracion: '2027-04-28' },
  { id: 'C-0004', titularId: 't1', area: 'Finanzas',          finalidad: 'Análisis de datos internos', estado: 'activo',    fechaOtorgamiento: '2025-06-10', fechaExpiracion: '2026-06-10' },
  { id: 'C-0005', titularId: 't2', area: 'Recursos Humanos',  finalidad: 'Investigación académica',    estado: 'expirado',  fechaOtorgamiento: '2024-03-01', fechaExpiracion: '2025-03-01' },
  { id: 'C-0006', titularId: 't3', area: 'Tecnología',        finalidad: 'Análisis de datos internos', estado: 'activo',    fechaOtorgamiento: '2026-01-20', fechaExpiracion: '2027-01-20' },
  { id: 'C-0007', titularId: 't1', area: 'Legal',             finalidad: 'Transferencia a terceros',   estado: 'revocado',  fechaOtorgamiento: '2025-09-05', fechaExpiracion: '2026-09-05' },
  { id: 'C-0008', titularId: 't2', area: 'Finanzas',          finalidad: 'Análisis de datos internos', estado: 'activo',    fechaOtorgamiento: '2026-02-14', fechaExpiracion: '2027-02-14' },
  { id: 'C-0009', titularId: 't3', area: 'Recursos Humanos',  finalidad: 'Marketing directo',          estado: 'pendiente', fechaOtorgamiento: '2026-04-01', fechaExpiracion: '2027-04-01' },
];
