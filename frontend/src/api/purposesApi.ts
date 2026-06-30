const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export { ApiError } from './usersApi';
import { ApiError } from './usersApi';

export interface PurposeResponse {
  id: string;
  code: string;
  name: string;
  description: string;
  shortDescription: string | null;
  consentStatement: string | null;
  required: boolean;
  revocable: boolean;
  presentationOrder: number | null;
  legalBasisId: string;
  legalBasisCode: string;
  legalBasisName: string;
  domainId: string;
  domainName: string;
  isActive: boolean;
  locked: boolean;
  createdBy: string;
  approvedBy: string | null;
  createdAt: string;
  updatedAt: string | null;
  hashSha256: string | null;
}

export interface CreatePurposePayload {
  code: string;
  name: string;
  description: string;
  shortDescription?: string;
  required: boolean;
  revocable: boolean;
  presentationOrder?: number;
  legalBasisId: string;
  domainId: string;
  purposeRequestId?: string;
  consentStatement?: string;
}

export interface UpdatePurposePayload {
  name?: string;
  description?: string;
  shortDescription?: string;
  required?: boolean;
  revocable?: boolean;
  presentationOrder?: number;
  legalBasisId?: string;
  consentStatement?: string;
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

export const getPurposes = async (token?: string | null): Promise<PurposeResponse[]> =>
  request<PurposeResponse[]>('/api/purposes', {}, token);

export const getPurpose = async (id: string, token?: string | null): Promise<PurposeResponse> =>
  request<PurposeResponse>(`/api/purposes/${id}`, {}, token);

export const getPurposesByDomain = async (
  domainId: string,
  token?: string | null,
): Promise<PurposeResponse[]> =>
  request<PurposeResponse[]>(`/api/purposes/domain/${domainId}`, {}, token);

export const createPurpose = async (
  payload: CreatePurposePayload,
  token?: string | null,
): Promise<PurposeResponse> =>
  request<PurposeResponse>(
    '/api/purposes',
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );

export const updatePurpose = async (
  id: string,
  payload: UpdatePurposePayload,
  token?: string | null,
): Promise<PurposeResponse> =>
  request<PurposeResponse>(
    `/api/purposes/${id}`,
    { method: 'PUT', body: JSON.stringify(payload) },
    token,
  );

export const deactivatePurpose = async (
  id: string,
  token?: string | null,
): Promise<PurposeResponse> =>
  request<PurposeResponse>(`/api/purposes/${id}`, { method: 'DELETE' }, token);
