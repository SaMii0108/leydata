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
  presentationOrder: number;
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
  updatedAt: string;
  hashSha256: string | null;
}

async function request<T>(path: string, options: RequestInit = {}, token?: string | null): Promise<T> {
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
