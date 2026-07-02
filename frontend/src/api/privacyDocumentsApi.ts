const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export { ApiError } from './usersApi';
import { ApiError } from './usersApi';

export type DocumentStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'PUBLISHED'
  | 'ARCHIVED';

export type DocumentCategory =
  | 'POLITICA_PRIVACIDAD'
  | 'AVISO_COOKIES'
  | 'DATOS_SENSIBLES'
  | 'MARKETING_DIRECTO'
  | 'MENORES_EDAD'
  | 'TRANSFERENCIA_TERCEROS';

export interface PrivacyDocumentDto {
  id: string;
  documentFamilyId: string;
  templateIds: string[];
  category: DocumentCategory;
  status: DocumentStatus;
  version: number;
  name: string;
  content: string | null;
  rejectionReason: string | null;
  hasPdf: boolean;
  hashSha256: string | null;
  publishAt: string | null;
  createdBy: string | null;
  approvedBy: string | null;
  createdAt: string;
  updatedAt: string;
  purposeIds: string[];
  isActive: boolean;
}

export interface CreateDocumentPayload {
  category: DocumentCategory;
  name: string;
  content?: string;
}

export interface UpdateDocumentPayload {
  name?: string;
  content?: string;
}

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

export const listDocuments = (
  category?: DocumentCategory | null,
  status?: DocumentStatus | null,
  token?: string | null,
): Promise<PrivacyDocumentDto[]> => {
  const params = new URLSearchParams();
  if (category) params.set('category', category);
  if (status) params.set('status', status);
  const q = params.toString() ? `?${params.toString()}` : '';
  return request<PrivacyDocumentDto[]>(`/api/privacy-documents${q}`, {}, token);
};

export const getDocument = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}`, {}, token);

export const createDocument = (
  payload: CreateDocumentPayload,
  token?: string | null,
): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(
    '/api/privacy-documents',
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );

export const updateDocument = (
  id: string,
  payload: UpdateDocumentPayload,
  token?: string | null,
): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(
    `/api/privacy-documents/${id}`,
    { method: 'PATCH', body: JSON.stringify(payload) },
    token,
  );

export const deactivateDocument = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}/deactivate`, { method: 'POST' }, token);

export const newDocumentVersion = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}/new-version`, { method: 'POST' }, token);

// ── Propósitos ─────────────────────────────────────────────────────────────────

export const addDocumentPurpose = (
  docId: string,
  purposeId: string,
  token?: string | null,
): Promise<void> =>
  requestVoid(`/api/privacy-documents/${docId}/purposes/${purposeId}`, { method: 'POST' }, token);

export const removeDocumentPurpose = (
  docId: string,
  purposeId: string,
  token?: string | null,
): Promise<void> =>
  requestVoid(`/api/privacy-documents/${docId}/purposes/${purposeId}`, { method: 'DELETE' }, token);

// ── Templates ──────────────────────────────────────────────────────────────────

export const addDocumentTemplate = (
  docId: string,
  templateId: string,
  token?: string | null,
): Promise<void> =>
  requestVoid(`/api/privacy-documents/${docId}/templates/${templateId}`, { method: 'POST' }, token);

export const removeDocumentTemplate = (
  docId: string,
  templateId: string,
  token?: string | null,
): Promise<void> =>
  requestVoid(`/api/privacy-documents/${docId}/templates/${templateId}`, { method: 'DELETE' }, token);

// ── Workflow ───────────────────────────────────────────────────────────────────

export const submitDocument = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}/submit`, { method: 'POST' }, token);

export const resubmitDocument = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}/resubmit`, { method: 'POST' }, token);

export const approveDocument = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}/approve`, { method: 'POST' }, token);

export const rejectDocument = (
  id: string,
  reason: string,
  token?: string | null,
): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(
    `/api/privacy-documents/${id}/reject`,
    { method: 'POST', body: JSON.stringify({ reason }) },
    token,
  );

export const publishDocument = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}/publish`, { method: 'POST' }, token);

export const archiveDocument = (id: string, token?: string | null): Promise<PrivacyDocumentDto> =>
  request<PrivacyDocumentDto>(`/api/privacy-documents/${id}/archive`, { method: 'POST' }, token);
