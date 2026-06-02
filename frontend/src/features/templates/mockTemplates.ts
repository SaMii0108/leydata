/**
 * Mock de plantillas de consentimiento.
 * En producción: GET /api/templates, POST /api/templates, PATCH /api/templates/:id/versions
 * MOCK — reemplazar con llamadas reales al integrar el backend.
 */
import type { TemplateConfig } from '../../components/common/ConsentPreview';

export type TemplateVersionStatus = 'activa' | 'archivada' | 'borrador';

export interface TemplateVersion {
  version: number;
  status: TemplateVersionStatus;
  config: TemplateConfig;
  savedAt: string;
}

export interface TemplateRecord {
  id: string;
  name: string;
  /** 'MULTI' cuando la plantilla está asociada a varios consentimientos. */
  consentId: string;
  /** Solo presente cuando consentId === 'MULTI'. */
  consentIds?: string[];
  consentFinalidad: string;
  consentArea: string;
  createdAt: string;
  updatedAt: string;
  versions: TemplateVersion[];
}

const BASE_CONFIG: TemplateConfig = {
  logo: '',
  primaryColor: '#4361ee',
  titleColor: '#111827',
  subtitleColor: '#6b7280',
  buttonLabel: 'Aceptar',
  buttonSize: 'md',
  buttonRadius: 'sm',
  domain: '',
  purpose: '',
  requiredFields: [],
};

export const MOCK_TEMPLATES: TemplateRecord[] = [
  {
    id: 'tmpl-001',
    name: 'Plantilla Marketing Directo',
    consentId: 'C-0001',
    consentFinalidad: 'Marketing directo',
    consentArea: 'Marketing',
    createdAt: '2026-04-10T10:00:00',
    updatedAt: '2026-04-18T15:30:00',
    versions: [
      {
        version: 1,
        status: 'archivada',
        config: {
          ...BASE_CONFIG,
          domain: 'Marketing',
          purpose: 'Uso de datos de contacto para envío de comunicaciones comerciales.',
          requiredFields: ['Nombre', 'Email'],
        },
        savedAt: '2026-04-10T10:00:00',
      },
      {
        version: 2,
        status: 'activa',
        config: {
          ...BASE_CONFIG,
          domain: 'Marketing',
          purpose:
            'Uso de datos de contacto para comunicaciones comerciales y ofertas personalizadas según Ley 21.719.',
          requiredFields: ['Nombre', 'Email', 'Teléfono'],
        },
        savedAt: '2026-04-18T15:30:00',
      },
    ],
  },
  {
    id: 'tmpl-002',
    name: 'Plantilla Análisis Interno',
    consentId: 'C-0002',
    consentFinalidad: 'Análisis de datos internos',
    consentArea: 'Tecnología',
    createdAt: '2026-03-05T09:00:00',
    updatedAt: '2026-03-05T09:00:00',
    versions: [
      {
        version: 1,
        status: 'activa',
        config: {
          ...BASE_CONFIG,
          domain: 'Análisis',
          purpose:
            'Tratamiento estadístico de datos internos conforme a Ley 21.719, Art. 12.',
          requiredFields: ['RUT', 'Nombre'],
        },
        savedAt: '2026-03-05T09:00:00',
      },
    ],
  },
];

/** Busca la plantilla asociada a un consentimiento. */
export const getTemplateByConsentId = (consentId: string): TemplateRecord | undefined =>
  MOCK_TEMPLATES.find((t) => t.consentId === consentId);

/** Busca una plantilla por su ID. */
export const getTemplateById = (id: string): TemplateRecord | undefined =>
  MOCK_TEMPLATES.find((t) => t.id === id);

/** Devuelve referencia al array completo (se lee en tiempo de render). */
export const getAllTemplates = (): TemplateRecord[] => MOCK_TEMPLATES;

/** Agrega una nueva plantilla (v1) al array. */
export const addTemplate = (template: TemplateRecord) => {
  MOCK_TEMPLATES.push(template);
};

/**
 * Agrega una nueva versión a una plantilla existente.
 * La versión activa anterior pasa a 'archivada'.
 * Solo una versión puede estar 'activa' al mismo tiempo.
 */
export const addTemplateVersion = (templateId: string, config: TemplateConfig): void => {
  const tmpl = MOCK_TEMPLATES.find((t) => t.id === templateId);
  if (!tmpl) return;
  tmpl.versions.forEach((v) => {
    if (v.status === 'activa') v.status = 'archivada';
  });
  tmpl.versions.push({
    version: tmpl.versions.length + 1,
    status: 'activa',
    config: { ...config },
    savedAt: new Date().toISOString(),
  });
  tmpl.updatedAt = new Date().toISOString();
};
