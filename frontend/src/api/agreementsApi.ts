const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export { ApiError } from './usersApi';
import { ApiError } from './usersApi';

// ── Status types ──────────────────────────────────────────────────────────────

export type AgreementStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED';

export type AgreementLifecycleStatus =
  | 'ALLOWED'
  | 'EXPIRED'
  | 'REQUIRES_RECONSENT'
  | 'PENDING';

export type AgreementPurposeStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED';

export type AgreementIntegrityStatus = 'OK' | 'MISMATCH' | 'PARTIAL';

// ── Label mappers ─────────────────────────────────────────────────────────────

export const AGREEMENT_STATUS_LABEL: Record<AgreementStatus, string> = {
  ACTIVE:  'Activo',
  REVOKED: 'Revocado',
  EXPIRED: 'Expirado',
};

export const AGREEMENT_LIFECYCLE_LABEL: Record<AgreementLifecycleStatus, string> = {
  ALLOWED:            'Permitido',
  EXPIRED:            'Expirado',
  REQUIRES_RECONSENT: 'Requiere reconsentimiento',
  PENDING:            'Pendiente',
};

export const AGREEMENT_PURPOSE_STATUS_LABEL: Record<AgreementPurposeStatus, string> = {
  ACTIVE:  'Activa',
  REVOKED: 'Revocada',
  EXPIRED: 'Expirada',
};

// ── Response DTOs ─────────────────────────────────────────────────────────────

export interface AgreementMetadataResponse {
  ipOrigin: string | null;
  userAgent: string | null;
  captureChannel: string | null;
  signatureToken: string | null;
  authProvider: string | null;
  extraVariables: string | null;
  createdAt: string;
}

export interface AgreementPurposeResponse {
  id: string;
  purposeId: string;
  accepted: boolean;
  purposeCode: string;
  purposeName: string;
  purposeDescription: string | null;
  purposeShortDescription: string | null;
  purposeRequired: boolean;
  purposeRevocable: boolean;
  legalBasisCode: string | null;
  status: AgreementPurposeStatus;
  expiresAt: string | null;
  createdAt: string;
}

export interface AgreementResponse {
  id: string;
  dataSubjectId: string;
  templateId: string;
  templateVersion: number;
  documentId: string;
  status: AgreementStatus;
  previousAgreementsId: string | null;
  expiration: string | null;
  createdAt: string;
  hashSha256: string | null;
  purposes: AgreementPurposeResponse[];
  metadata: AgreementMetadataResponse | null;
}

// ── Request payloads ──────────────────────────────────────────────────────────

export interface PurposeDecisionRequest {
  purposeId: string;
  accepted: boolean;
}

export interface AgreementMetadataRequest {
  captureChannel?: string;
  signatureToken?: string;
  authProvider?: string;
  extraVariables?: string;
}

export interface CreateAgreementRequest {
  subjectIdentifier?: string;
  dataSubjectId?: string;
  templateId: string;
  documentId?: string;
  purposes: PurposeDecisionRequest[];
  metadata?: AgreementMetadataRequest;
}

// ── Lifecycle / B2B DTOs ──────────────────────────────────────────────────────

export interface ConsentLifecycleResponse {
  subjectIdentifier: string;
  templateKey: string;
  status: AgreementLifecycleStatus;
  agreementId: string | null;
  agreementTemplateVersion: number | null;
  currentTemplateVersion: number;
  earliestExpiresAt: string | null;
}

export interface PurposeSummaryItem {
  purposeId: string;
  purposeCode: string;
  purposeName: string;
  accepted: boolean;
  status: AgreementPurposeStatus;
  expiresAt: string | null;
  acceptedAt: string;
  required: boolean;
  revocable: boolean;
}

export interface SubjectSummaryResponse {
  subjectIdentifier: string;
  domainId: string;
  agreementId: string;
  templateId: string;
  templateKey: string | null;
  templateVersion: number;
  documentId: string;
  purposes: PurposeSummaryItem[];
}

// ── Template resolution (endpoint /api/templates/resolve — flujo de consent) ──

export interface TemplateResolutionResponse {
  templateId: string;
  domainId: string;
  templateKey: string;
  version: number;
  documentId: string | null;
}

// ── Audit trace DTOs ──────────────────────────────────────────────────────────

export interface AgreementTraceDocumentLink {
  documentId: string;
  version: number | null;
  isValid: boolean;
}

export interface AgreementTraceTemplateLink {
  templateId: string;
  templateKey: string | null;
  version: number | null;
  isValid: boolean;
}

export interface AgreementTracePurposeLink {
  purposeId: string;
  purposeFamilyId: string | null;
  version: number | null;
  code: string;
  accepted: boolean;
  isValid: boolean;
  integrityStatus: 'OK' | 'INTEGRITY_MISMATCH' | 'UNKNOWN';
}

export interface AgreementTraceResponse {
  agreementId: string;
  dataSubjectId: string;
  createdAt: string;
  document: AgreementTraceDocumentLink;
  template: AgreementTraceTemplateLink;
  purposes: AgreementTracePurposeLink[];
  overallIntegrity: AgreementIntegrityStatus;
}

// ── Filter types ──────────────────────────────────────────────────────────────

export interface AgreementListFilters {
  dataSubjectId?: string;
  templateId?: string;
  status?: AgreementStatus;
}

// ── HTTP helpers ──────────────────────────────────────────────────────────────

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string | null,
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers as Record<string, string> | undefined ?? {}),
  };
  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const message = body?.message ?? body?.error ?? res.statusText ?? `Error ${res.status}`;
    throw new ApiError(res.status, message);
  }
  return res.json() as Promise<T>;
}

// ── Agreement CRUD ────────────────────────────────────────────────────────────

export const getAgreements = (
  filters?: AgreementListFilters,
  token?: string | null,
): Promise<AgreementResponse[]> => {
  const params = new URLSearchParams();
  if (filters?.dataSubjectId) params.set('dataSubjectId', filters.dataSubjectId);
  if (filters?.templateId)    params.set('templateId', filters.templateId);
  if (filters?.status)        params.set('status', filters.status);
  const q = params.toString() ? `?${params.toString()}` : '';
  return request<AgreementResponse[]>(`/api/agreements${q}`, {}, token);
};

export const getAgreement = (
  id: string,
  token?: string | null,
): Promise<AgreementResponse> =>
  request<AgreementResponse>(`/api/agreements/${id}`, {}, token);

export const createAgreement = (
  payload: CreateAgreementRequest,
  token?: string | null,
): Promise<AgreementResponse> =>
  request<AgreementResponse>(
    '/api/agreements',
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );

export const revokeAgreement = (
  id: string,
  subjectId: string,
  token?: string | null,
): Promise<AgreementResponse> =>
  request<AgreementResponse>(
    `/api/agreements/${id}/revoke`,
    { method: 'PATCH', body: JSON.stringify({ subjectId }) },
    token,
  );

// ── Lifecycle check ───────────────────────────────────────────────────────────

export const getLifecycleCheck = (
  subjectIdentifier: string,
  domainId: string,
  templateKey: string,
  token?: string | null,
): Promise<ConsentLifecycleResponse> => {
  const params = new URLSearchParams({ subjectIdentifier, domainId, templateKey });
  return request<ConsentLifecycleResponse>(
    `/api/agreements/lifecycle-check?${params.toString()}`,
    {},
    token,
  );
};

export const getSubjectSummary = (
  subjectIdentifier: string,
  domainId: string,
  token?: string | null,
): Promise<SubjectSummaryResponse[]> => {
  const params = new URLSearchParams({ subjectIdentifier, domainId });
  return request<SubjectSummaryResponse[]>(
    `/api/agreements/subject-summary?${params.toString()}`,
    {},
    token,
  );
};

// ── Template resolution (pertenece a /api/templates/resolve — uso en consent) ─

export const resolveTemplate = (
  domainId: string,
  templateKey: string,
  token?: string | null,
): Promise<TemplateResolutionResponse> => {
  const params = new URLSearchParams({ domainId, templateKey });
  return request<TemplateResolutionResponse>(
    `/api/templates/resolve?${params.toString()}`,
    {},
    token,
  );
};

// ── Audit trace (requiere rol ADMIN) ─────────────────────────────────────────

export const traceAgreement = (
  id: string,
  token?: string | null,
): Promise<AgreementTraceResponse> =>
  request<AgreementTraceResponse>(`/api/audit/trace/agreement/${id}`, {}, token);
