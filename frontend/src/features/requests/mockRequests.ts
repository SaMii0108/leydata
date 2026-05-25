/**
 * Mock de solicitudes de propósito de tratamiento de datos.
 * En producción se obtienen de: GET /api/purpose-requests (DPO)
 *                               GET /api/purpose-requests/my (JEFE_DOMINIO)
 * MOCK — reemplazar con llamadas reales al integrar el backend.
 */

export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface PurposeRequest {
  id: string;
  title: string;
  justification: string;
  /** JSON string con campos y retención — parsearlo al mostrar */
  requestedData: string;
  domainId: string;
  domainName: string;
  requesterId: string;
  requesterName: string;
  status: RequestStatus;
  reviewerId: string | null;
  reviewerName: string | null;
  /** Obligatorio si status === 'REJECTED' (Ley 21.719 trazabilidad) */
  reviewNotes: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export const MOCK_REQUESTS: PurposeRequest[] = [
  {
    id: 'req-001',
    title: 'Campaña email marketing Q1',
    justification: 'Necesitamos usar los datos de contacto para enviar comunicaciones comerciales relacionadas con nuestros nuevos productos.',
    requestedData: JSON.stringify({ fields: ['email', 'nombre', 'teléfono'], retention: '6 meses' }),
    domainId: 'd1',
    domainName: 'Marketing',
    requesterId: 'u4',
    requesterName: 'Pedro Soto',
    status: 'PENDING',
    reviewerId: null,
    reviewerName: null,
    reviewNotes: null,
    createdAt: '2026-05-10T09:00:00',
    updatedAt: null,
  },
  {
    id: 'req-002',
    title: 'Análisis de desempeño anual',
    justification: 'Evaluación interna de rendimiento laboral para el proceso de promociones 2026.',
    requestedData: JSON.stringify({ fields: ['rut', 'nombre', 'cargo', 'evaluación'], retention: '12 meses' }),
    domainId: 'd2',
    domainName: 'Recursos Humanos',
    requesterId: 'u3',
    requesterName: 'María López',
    status: 'APPROVED',
    reviewerId: 'u2',
    reviewerName: 'Carlos Ruiz',
    reviewNotes: 'Cumple con los requisitos de la Ley 21.719. Retención justificada.',
    createdAt: '2026-05-05T14:20:00',
    updatedAt: '2026-05-07T10:15:00',
  },
  {
    id: 'req-003',
    title: 'Integración con plataforma externa de CRM',
    justification: 'Transferencia de datos de clientes a tercero para gestión de relaciones comerciales.',
    requestedData: JSON.stringify({ fields: ['email', 'nombre', 'historial_compras'], retention: '24 meses' }),
    domainId: 'd1',
    domainName: 'Marketing',
    requesterId: 'u4',
    requesterName: 'Pedro Soto',
    status: 'REJECTED',
    reviewerId: 'u2',
    reviewerName: 'Carlos Ruiz',
    reviewNotes: 'No se justifica la retención de 24 meses. Máximo permitido es 12 meses según Art. 14. Además, la transferencia a terceros requiere autorización expresa del titular.',
    createdAt: '2026-04-28T11:00:00',
    updatedAt: '2026-04-30T16:45:00',
  },
  {
    id: 'req-004',
    title: 'Monitoreo de sistemas de seguridad TI',
    justification: 'Registro de accesos y actividad del sistema para auditoría de seguridad interna.',
    requestedData: JSON.stringify({ fields: ['usuario', 'ip', 'timestamp', 'acción'], retention: '3 meses' }),
    domainId: 'd3',
    domainName: 'Tecnología',
    requesterId: 'u5',
    requesterName: 'Lucía Vargas',
    status: 'PENDING',
    reviewerId: null,
    reviewerName: null,
    reviewNotes: null,
    createdAt: '2026-05-15T08:30:00',
    updatedAt: null,
  },
];

/** Agrega una nueva solicitud al array (persiste durante la sesión) */
export const addMockRequest = (req: PurposeRequest) => {
  MOCK_REQUESTS.push(req);
};

/** Actualiza el estado de una solicitud (revisión DPO) */
export const reviewMockRequest = (
  id: string,
  status: RequestStatus,
  reviewerId: string,
  reviewerName: string,
  reviewNotes: string | null,
) => {
  const idx = MOCK_REQUESTS.findIndex((r) => r.id === id);
  if (idx !== -1) {
    MOCK_REQUESTS[idx].status = status;
    MOCK_REQUESTS[idx].reviewerId = reviewerId;
    MOCK_REQUESTS[idx].reviewerName = reviewerName;
    MOCK_REQUESTS[idx].reviewNotes = reviewNotes;
    MOCK_REQUESTS[idx].updatedAt = new Date().toISOString();
  }
};
