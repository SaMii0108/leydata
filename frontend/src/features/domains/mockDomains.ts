export interface MockDomain {
  id: string;
  code: string;
  name: string;
  description: string;
  active: boolean;
}

/** Estado global de dominios durante la sesión (mutable, igual que MOCK_USERS) */
export const MOCK_DOMAINS: MockDomain[] = [
  { id: 'd1', code: 'MKT',  name: 'Marketing',       description: 'Área de marketing y comunicaciones digitales', active: true  },
  { id: 'd2', code: 'RRHH', name: 'Recursos Humanos', description: 'Gestión de personas y capital humano',          active: true  },
  { id: 'd3', code: 'TEC',  name: 'Tecnología',       description: 'Área de tecnología, sistemas e innovación',     active: true  },
  { id: 'd4', code: 'LEG',  name: 'Legal',            description: 'Área jurídica y cumplimiento normativo',        active: true  },
  { id: 'd5', code: 'FIN',  name: 'Finanzas',         description: 'Área financiera y contabilidad',                active: false },
];

/** Agrega un dominio nuevo al array */
export const addMockDomain = (domain: MockDomain) => {
  MOCK_DOMAINS.push(domain);
};

/** Actualiza campos de un dominio (ej. active) */
export const updateMockDomain = (id: string, updates: Partial<MockDomain>) => {
  const idx = MOCK_DOMAINS.findIndex((d) => d.id === id);
  if (idx !== -1) Object.assign(MOCK_DOMAINS[idx], updates);
};

/** Verifica si ya existe un dominio con ese código (case-insensitive) */
export const domainCodeExists = (code: string, excludeId?: string): boolean =>
  MOCK_DOMAINS.some(
    (d) => d.code.toUpperCase() === code.toUpperCase() && d.id !== excludeId,
  );
