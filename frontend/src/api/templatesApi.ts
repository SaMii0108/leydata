const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export { ApiError } from './usersApi';
import { ApiError } from './usersApi';

// ── Tipos ─────────────────────────────────────────────────────────────────────

export type TemplateStatus = 'DRAFT' | 'APPROVED' | 'ACTIVE';

export interface TemplateResponse {
  id: string;
  templateKey: string;
  version: number;
  name: string;
  description: string | null;
  title: string | null;
  isActive: boolean;
  status: TemplateStatus;
  changeReason: string | null;
  createdBy: string;
  createdAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  activationDate: string | null;
  domainId: string;
}

export interface TemplatePurposeResponse {
  purposeId: string;
  purposeName: string;
  orderPosition: number;
  isVisible: boolean;
}

export interface TemplateVerifyResponse {
  templateId: string;
  version: number;
  hashMatch: boolean;
  storedHash: string | null;
  computedHash: string | null;
  message: string;
}

export interface CreateTemplatePayload {
  domainId: string;
  templateKey: string;
  name: string;
  description?: string;
  title?: string;
}

export interface TemplateListFilters {
  templateKey?: string;
  isActive?: boolean;
  createdBy?: string;
  approvedBy?: string;
  createdAfter?: string;
  createdBefore?: string;
}

export interface AddTemplatePurposePayload {
  purposeId: string;
  orderPosition: number;
  isVisible?: boolean;
}

export interface UpdateTemplatePurposePayload {
  orderPosition?: number;
  isVisible?: boolean;
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

// Para endpoints que devuelven 204 No Content (sin cuerpo en la respuesta).
async function requestVoid(
  path: string,
  options: RequestInit = {},
  token?: string | null,
): Promise<void> {
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
}

// ── CRUD ──────────────────────────────────────────────────────────────────────

export const getTemplates = async (
  filters?: TemplateListFilters,
  token?: string | null,
): Promise<TemplateResponse[]> => {
  const params = new URLSearchParams();
  if (filters?.templateKey) params.set('templateKey', filters.templateKey);
  if (filters?.isActive !== undefined) params.set('isActive', String(filters.isActive));
  if (filters?.createdBy) params.set('createdBy', filters.createdBy);
  if (filters?.approvedBy) params.set('approvedBy', filters.approvedBy);
  if (filters?.createdAfter) params.set('createdAfter', filters.createdAfter);
  if (filters?.createdBefore) params.set('createdBefore', filters.createdBefore);
  const query = params.toString() ? `?${params.toString()}` : '';
  return request<TemplateResponse[]>(`/api/templates${query}`, {}, token);
};

export const getTemplate = async (
  id: string,
  token?: string | null,
): Promise<TemplateResponse> =>
  request<TemplateResponse>(`/api/templates/${id}`, {}, token);

export const createTemplate = async (
  payload: CreateTemplatePayload,
  token?: string | null,
): Promise<TemplateResponse> =>
  request<TemplateResponse>(
    '/api/templates',
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );

export const newTemplateVersion = async (
  id: string,
  token?: string | null,
): Promise<TemplateResponse> =>
  request<TemplateResponse>(
    `/api/templates/${id}/new-version`,
    { method: 'POST' },
    token,
  );

// ── Consultas de familia ──────────────────────────────────────────────────────

export const getTemplateFamily = async (
  templateKey: string,
  domainId: string,
  token?: string | null,
): Promise<TemplateResponse[]> =>
  request<TemplateResponse[]>(`/api/templates/family/${templateKey}?domainId=${domainId}`, {}, token);

export const getActiveTemplate = async (
  templateKey: string,
  token?: string | null,
): Promise<TemplateResponse> =>
  request<TemplateResponse>(`/api/templates/active/${templateKey}`, {}, token);

// ── Integridad ────────────────────────────────────────────────────────────────

export const verifyTemplate = async (
  id: string,
  token?: string | null,
): Promise<TemplateVerifyResponse> =>
  request<TemplateVerifyResponse>(`/api/templates/${id}/verify`, {}, token);

// ── Workflow ──────────────────────────────────────────────────────────────────

export const approveTemplate = async (
  id: string,
  token?: string | null,
): Promise<TemplateResponse> =>
  request<TemplateResponse>(
    `/api/templates/${id}/approve`,
    { method: 'POST' },
    token,
  );

export const activateTemplate = async (
  id: string,
  token?: string | null,
): Promise<TemplateResponse> =>
  request<TemplateResponse>(
    `/api/templates/${id}/activate`,
    { method: 'POST' },
    token,
  );

// ── Finalidades vinculadas ────────────────────────────────────────────────────

export const getTemplatePurposes = async (
  templateId: string,
  token?: string | null,
): Promise<TemplatePurposeResponse[]> =>
  request<TemplatePurposeResponse[]>(`/api/templates/${templateId}/purposes`, {}, token);

export const addTemplatePurpose = async (
  templateId: string,
  payload: AddTemplatePurposePayload,
  token?: string | null,
): Promise<void> =>
  requestVoid(
    `/api/templates/${templateId}/purposes`,
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );

export const removeTemplatePurpose = async (
  templateId: string,
  purposeId: string,
  token?: string | null,
): Promise<void> =>
  requestVoid(
    `/api/templates/${templateId}/purposes/${purposeId}`,
    { method: 'DELETE' },
    token,
  );

export const updateTemplatePurpose = async (
  templateId: string,
  purposeId: string,
  payload: UpdateTemplatePurposePayload,
  token?: string | null,
): Promise<TemplatePurposeResponse> =>
  request<TemplatePurposeResponse>(
    `/api/templates/${templateId}/purposes/${purposeId}`,
    { method: 'PATCH', body: JSON.stringify(payload) },
    token,
  );
