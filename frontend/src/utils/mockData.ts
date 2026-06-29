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

// ── Tipos de datos ────────────────────────────────────────────────────────────

export type TipoDato = 'Texto' | 'Email' | 'Teléfono' | 'Fecha' | 'Número' | 'RUT';

// ── Documentos de Privacidad ───────────────────────────────────────────────────

export type DocumentoEstado = 'vigente' | 'borrador' | 'obsoleto';

export interface DocumentoPrivacidad {
  id: string;
  nombre: string;
  descripcion: string;
  version: string;
  estado: DocumentoEstado;
  fechaVigencia: string;
}

export const DOCUMENTOS_PRIVACIDAD: DocumentoPrivacidad[] = [
  { id: 'doc-001', nombre: 'Política de Privacidad General', descripcion: 'Documento maestro que establece los principios generales de tratamiento de datos personales en la organización, conforme a la Ley 21.719.', version: '2.1', estado: 'vigente', fechaVigencia: '2026-12-31' },
  { id: 'doc-002', nombre: 'Política de Marketing', descripcion: 'Regula el tratamiento de datos personales para actividades de comunicación comercial, campañas promocionales y análisis de audiencias.', version: '1.0', estado: 'vigente', fechaVigencia: '2026-12-31' },
  { id: 'doc-003', nombre: 'Política de Recursos Humanos', descripcion: 'Establece las condiciones para el tratamiento de datos del personal en procesos de selección, contratación, nómina y beneficios.', version: '1.5', estado: 'vigente', fechaVigencia: '2027-03-31' },
  { id: 'doc-004', nombre: 'Política de Análisis de Datos', descripcion: 'Define los parámetros para el uso de datos anonimizados y seudonimizados en análisis estadísticos y mejora de productos.', version: '1.0', estado: 'vigente', fechaVigencia: '2027-06-30' },
  { id: 'doc-005', nombre: 'Política de Atención de Pacientes', descripcion: 'Marco normativo para el tratamiento de datos clínicos y de salud en procesos de atención médica y seguimiento de pacientes.', version: '1.0', estado: 'borrador', fechaVigencia: '2027-12-31' },
];

export const getDocumento = (id: string): DocumentoPrivacidad | undefined =>
  DOCUMENTOS_PRIVACIDAD.find((d) => d.id === id);

export const getDocumentosVigentes = (): DocumentoPrivacidad[] =>
  DOCUMENTOS_PRIVACIDAD.filter((d) => d.estado === 'vigente');

// ── Solicitudes de Finalidad ───────────────────────────────────────────────────

export type SolicitudEstado = 'pendiente' | 'aprobada' | 'rechazada';

export interface SolicitudDato {
  nombre: string;
  tipo: TipoDato;
  obligatorio: boolean;
}

export interface SolicitudFinalidad {
  id: string;
  nombre: string;
  descripcion: string;
  dominio: string;
  justificacion: string;
  datos: SolicitudDato[];
  estado: SolicitudEstado;
  creadoPor: string;
  creadoEn: string;
  revisadoPor?: string;
  revisadoEn?: string;
  notaRevision?: string;
  finalidadId?: string;
}

export const SOLICITUDES: SolicitudFinalidad[] = [
  { id: 'sol-001', nombre: 'Marketing directo', descripcion: 'Envío de campañas y comunicaciones comerciales personalizadas basadas en los datos del titular.', dominio: 'Marketing', justificacion: 'Necesitamos contactar a clientes con ofertas y promociones relevantes para incrementar conversión y fidelización.', datos: [{ nombre: 'Nombre', tipo: 'Texto', obligatorio: true }, { nombre: 'Correo electrónico', tipo: 'Email', obligatorio: true }, { nombre: 'Teléfono', tipo: 'Teléfono', obligatorio: false }], estado: 'aprobada', creadoPor: 'Laura Vega', creadoEn: '2026-03-01', revisadoPor: 'Carlos Ruiz', revisadoEn: '2026-03-05', finalidadId: 'fin-a001' },
  { id: 'sol-002', nombre: 'Gestión de RRHH', descripcion: 'Tratamiento de datos del personal para procesos administrativos, tributarios y contractuales.', dominio: 'Recursos Humanos', justificacion: 'Requerido para cumplir con obligaciones laborales, tributarias y de seguridad social vigentes conforme a la legislación chilena.', datos: [{ nombre: 'RUT', tipo: 'RUT', obligatorio: true }, { nombre: 'Dirección', tipo: 'Texto', obligatorio: true }, { nombre: 'Fecha de nacimiento', tipo: 'Fecha', obligatorio: false }], estado: 'aprobada', creadoPor: 'Laura Vega', creadoEn: '2026-02-15', revisadoPor: 'Carlos Ruiz', revisadoEn: '2026-02-20', finalidadId: 'fin-a002' },
  { id: 'sol-003', nombre: 'Análisis estadístico interno', descripcion: 'Uso de datos anonimizados para análisis de comportamiento y mejora de productos y servicios.', dominio: 'Análisis', justificacion: 'Mejorar productos mediante análisis de patrones de comportamiento de usuarios, con datos debidamente anonimizados.', datos: [{ nombre: 'Correo electrónico', tipo: 'Email', obligatorio: true }, { nombre: 'Fecha de nacimiento', tipo: 'Fecha', obligatorio: false }], estado: 'aprobada', creadoPor: 'Laura Vega', creadoEn: '2026-04-01', revisadoPor: 'Carlos Ruiz', revisadoEn: '2026-04-10', finalidadId: 'fin-a003' },
  { id: 'sol-004', nombre: 'Atención de pacientes', descripcion: 'Tratamiento de datos clínicos para seguimiento médico y coordinación de citas.', dominio: 'Legal', justificacion: 'Registro obligatorio de consentimientos para procedimientos médicos conforme a normativa sanitaria vigente.', datos: [{ nombre: 'Nombre completo', tipo: 'Texto', obligatorio: true }, { nombre: 'RUT', tipo: 'RUT', obligatorio: true }, { nombre: 'Fecha de nacimiento', tipo: 'Fecha', obligatorio: true }, { nombre: 'Teléfono de contacto', tipo: 'Teléfono', obligatorio: false }], estado: 'pendiente', creadoPor: 'Laura Vega', creadoEn: '2026-06-10' },
  { id: 'sol-005', nombre: 'Transferencia a socios comerciales', descripcion: 'Cesión de datos a terceros socios para cumplir acuerdos comerciales vigentes.', dominio: 'Legal', justificacion: 'Requerido para ejecutar contratos con proveedores estratégicos que procesan datos en nombre de la empresa.', datos: [{ nombre: 'Nombre', tipo: 'Texto', obligatorio: true }, { nombre: 'Correo electrónico', tipo: 'Email', obligatorio: true }], estado: 'rechazada', creadoPor: 'Laura Vega', creadoEn: '2026-05-01', revisadoPor: 'Carlos Ruiz', revisadoEn: '2026-05-10', notaRevision: 'No cumple con los requisitos del Art. 14 de la Ley 21.719 para transferencia internacional de datos personales.' },
];

let _solCounter = 6;

export const getSolicitud = (id: string): SolicitudFinalidad | undefined =>
  SOLICITUDES.find((s) => s.id === id);

export const addSolicitudFinalidad = (
  data: Omit<SolicitudFinalidad, 'id' | 'estado' | 'creadoEn'>,
): SolicitudFinalidad => {
  const solicitud: SolicitudFinalidad = {
    ...data,
    id: `sol-${String(_solCounter++).padStart(3, '0')}`,
    estado: 'pendiente',
    creadoEn: new Date().toISOString().split('T')[0],
  };
  SOLICITUDES.push(solicitud);
  return solicitud;
};

export const rechazarSolicitud = (id: string, revisadoPor: string, nota: string): void => {
  const idx = SOLICITUDES.findIndex((s) => s.id === id);
  if (idx === -1) return;
  SOLICITUDES[idx] = {
    ...SOLICITUDES[idx],
    estado: 'rechazada',
    revisadoPor,
    revisadoEn: new Date().toISOString().split('T')[0],
    notaRevision: nota,
  };
};

// ── Finalidades (entidades aprobadas) ─────────────────────────────────────────

export interface FinalidadDato {
  nombre: string;
  tipo: TipoDato;
  obligatorio: boolean;
}

export interface Finalidad {
  id: string;
  nombre: string;
  descripcion: string;
  dominio: string;
  documentoPrivacidadId: string;
  datos: FinalidadDato[];
  estado: 'activa' | 'inactiva';
  creadoEn: string;
  solicitudId: string;
}

export const FINALIDADES: Finalidad[] = [
  { id: 'fin-a001', nombre: 'Marketing directo', descripcion: 'Envío de campañas y comunicaciones comerciales personalizadas basadas en los datos del titular.', dominio: 'Marketing', documentoPrivacidadId: 'doc-002', datos: [{ nombre: 'Nombre', tipo: 'Texto', obligatorio: true }, { nombre: 'Correo electrónico', tipo: 'Email', obligatorio: true }, { nombre: 'Teléfono', tipo: 'Teléfono', obligatorio: false }], estado: 'activa', creadoEn: '2026-03-05', solicitudId: 'sol-001' },
  { id: 'fin-a002', nombre: 'Gestión de RRHH', descripcion: 'Tratamiento de datos del personal para procesos administrativos, tributarios y contractuales.', dominio: 'Recursos Humanos', documentoPrivacidadId: 'doc-003', datos: [{ nombre: 'RUT', tipo: 'RUT', obligatorio: true }, { nombre: 'Dirección', tipo: 'Texto', obligatorio: true }, { nombre: 'Fecha de nacimiento', tipo: 'Fecha', obligatorio: false }], estado: 'activa', creadoEn: '2026-02-20', solicitudId: 'sol-002' },
  { id: 'fin-a003', nombre: 'Análisis estadístico interno', descripcion: 'Uso de datos anonimizados para análisis de comportamiento y mejora de productos.', dominio: 'Análisis', documentoPrivacidadId: 'doc-004', datos: [{ nombre: 'Correo electrónico', tipo: 'Email', obligatorio: true }, { nombre: 'Fecha de nacimiento', tipo: 'Fecha', obligatorio: false }], estado: 'activa', creadoEn: '2026-04-10', solicitudId: 'sol-003' },
];

let _finCounter = 4;

export const getFinalidad = (id: string): Finalidad | undefined =>
  FINALIDADES.find((f) => f.id === id);

export const getFinalidadesActivas = (): Finalidad[] =>
  FINALIDADES.filter((f) => f.estado === 'activa');

export const aprobarSolicitud = (
  solicitudId: string,
  revisadoPor: string,
  documentoPrivacidadId: string,
  nota?: string,
): Finalidad | null => {
  const idx = SOLICITUDES.findIndex((s) => s.id === solicitudId);
  if (idx === -1) return null;
  const solicitud = SOLICITUDES[idx];
  const finalidad: Finalidad = {
    id: `fin-${String(_finCounter++).padStart(4, '0')}`,
    nombre: solicitud.nombre,
    descripcion: solicitud.descripcion,
    dominio: solicitud.dominio,
    documentoPrivacidadId,
    datos: solicitud.datos.map((d) => ({ nombre: d.nombre, tipo: d.tipo, obligatorio: d.obligatorio })),
    estado: 'activa',
    creadoEn: new Date().toISOString().split('T')[0],
    solicitudId,
  };
  FINALIDADES.push(finalidad);
  SOLICITUDES[idx] = { ...solicitud, estado: 'aprobada', revisadoPor, revisadoEn: new Date().toISOString().split('T')[0], notaRevision: nota, finalidadId: finalidad.id };
  return finalidad;
};

// ── Plantillas de Consentimiento ───────────────────────────────────────────────

export interface DataItem {
  id: string;
  nombre: string;
  tipo: TipoDato;
  obligatorio: boolean;
  descripcionTitular: string;
}

export interface TemplateVersion {
  version: number;
  fecha: string;
  autor: string;
  nota: string;
}

export type TemplateEstado = 'activa' | 'inactiva' | 'borrador';

export interface Template {
  id: string;
  nombre: string;
  descripcion: string;
  finalidadId: string;
  baseLicitud: string;
  estado: TemplateEstado;
  dominio: string;
  version: number;
  creadoPor: string;
  creadoEn: string;
  dataItems: DataItem[];
  primaryColor: string;
  buttonLabel: string;
  versiones: TemplateVersion[];
}

export const BASES_LICITUD = [
  'Consentimiento explícito del titular (Art. 12 letra a)',
  'Ejecución de relación contractual (Art. 12 letra b)',
  'Cumplimiento de obligación legal (Art. 12 letra c)',
  'Protección de intereses vitales (Art. 12 letra d)',
  'Misión de interés público (Art. 12 letra e)',
  'Interés legítimo del responsable (Art. 12 letra f)',
] as const;

export const DOMINIOS = ['Marketing', 'Recursos Humanos', 'Tecnología', 'Legal', 'Finanzas', 'Análisis'] as const;

export const TEMPLATES: Template[] = [
  {
    id: 'tpl-001',
    nombre: 'Consentimiento Marketing',
    descripcion: 'Plantilla para captura de consentimiento de marketing directo.',
    finalidadId: 'fin-a001',
    baseLicitud: 'Consentimiento explícito del titular (Art. 12 letra a)',
    estado: 'activa',
    dominio: 'Marketing',
    version: 2,
    creadoPor: 'Carlos Ruiz',
    creadoEn: '2026-04-15',
    dataItems: [
      { id: 'di-001', nombre: 'Nombre', tipo: 'Texto', obligatorio: true, descripcionTitular: 'Identificación del titular para personalizar las comunicaciones de marketing.' },
      { id: 'di-002', nombre: 'Correo electrónico', tipo: 'Email', obligatorio: true, descripcionTitular: 'Utilizaremos este correo para enviar notificaciones y comunicaciones de marketing.' },
      { id: 'di-003', nombre: 'Teléfono', tipo: 'Teléfono', obligatorio: false, descripcionTitular: 'Utilizaremos este número para contactarte con ofertas y promociones.' },
    ],
    primaryColor: '#4361ee',
    buttonLabel: 'Aceptar',
    versiones: [
      { version: 1, fecha: '2026-03-10', autor: 'Carlos Ruiz', nota: 'Versión inicial.' },
      { version: 2, fecha: '2026-04-15', autor: 'Carlos Ruiz', nota: 'Datos actualizados desde finalidad aprobada.' },
    ],
  },
  {
    id: 'tpl-002',
    nombre: 'Consentimiento RRHH',
    descripcion: 'Plantilla para el tratamiento de datos del personal de la organización.',
    finalidadId: 'fin-a002',
    baseLicitud: 'Ejecución de relación contractual (Art. 12 letra b)',
    estado: 'activa',
    dominio: 'Recursos Humanos',
    version: 1,
    creadoPor: 'Carlos Ruiz',
    creadoEn: '2026-03-10',
    dataItems: [
      { id: 'di-004', nombre: 'RUT', tipo: 'RUT', obligatorio: true, descripcionTitular: 'Requerido para procesos tributarios y de seguridad social.' },
      { id: 'di-005', nombre: 'Dirección', tipo: 'Texto', obligatorio: true, descripcionTitular: 'Utilizaremos esta dirección para validar información contractual y laboral.' },
      { id: 'di-006', nombre: 'Fecha de nacimiento', tipo: 'Fecha', obligatorio: false, descripcionTitular: 'Para cálculo de beneficios y tramos etarios aplicables.' },
    ],
    primaryColor: '#7c3aed',
    buttonLabel: 'Acepto',
    versiones: [
      { version: 1, fecha: '2026-03-10', autor: 'Carlos Ruiz', nota: 'Versión inicial.' },
    ],
  },
  {
    id: 'tpl-003',
    nombre: 'Análisis de Datos',
    descripcion: 'Plantilla para investigación y análisis estadístico interno.',
    finalidadId: 'fin-a003',
    baseLicitud: 'Interés legítimo del responsable (Art. 12 letra f)',
    estado: 'borrador',
    dominio: 'Análisis',
    version: 1,
    creadoPor: 'Carlos Ruiz',
    creadoEn: '2026-05-20',
    dataItems: [
      { id: 'di-007', nombre: 'Correo electrónico', tipo: 'Email', obligatorio: true, descripcionTitular: 'Utilizaremos este dato para segmentación y análisis de comportamiento.' },
      { id: 'di-008', nombre: 'Fecha de nacimiento', tipo: 'Fecha', obligatorio: false, descripcionTitular: 'Para segmentación demográfica por rango etario.' },
    ],
    primaryColor: '#059669',
    buttonLabel: 'Autorizar',
    versiones: [
      { version: 1, fecha: '2026-05-20', autor: 'Carlos Ruiz', nota: 'Versión inicial (borrador).' },
    ],
  },
];

let _tplCounter = 4;

export const getTemplate = (id: string): Template | undefined =>
  TEMPLATES.find((t) => t.id === id);

export const addTemplate = (
  data: Omit<Template, 'id' | 'version' | 'creadoEn' | 'versiones'>,
): Template => {
  const today = new Date().toISOString().split('T')[0];
  const tpl: Template = {
    ...data,
    id: `tpl-${String(_tplCounter++).padStart(3, '0')}`,
    version: 1,
    creadoEn: today,
    versiones: [{ version: 1, fecha: today, autor: data.creadoPor, nota: 'Versión inicial.' }],
  };
  TEMPLATES.push(tpl);
  return tpl;
};

export const updateTemplate = (
  id: string,
  updates: Partial<Omit<Template, 'id' | 'versiones'>>,
  autorEdicion: string,
): void => {
  const idx = TEMPLATES.findIndex((t) => t.id === id);
  if (idx === -1) return;
  const prev = TEMPLATES[idx];
  const newVersion = prev.version + 1;
  const today = new Date().toISOString().split('T')[0];
  TEMPLATES[idx] = {
    ...prev,
    ...updates,
    version: newVersion,
    versiones: [
      ...prev.versiones,
      { version: newVersion, fecha: today, autor: autorEdicion, nota: `Versión ${newVersion} — plantilla actualizada.` },
    ],
  };
};
