const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export { ApiError } from './usersApi';
import { ApiError } from './usersApi';

export interface DomainDto {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
}

export interface CreateDomainPayload {
  code: string;
  name: string;
  description: string;
  jefeId: string | null;
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

export const getAllDomains = async (token?: string | null): Promise<DomainDto[]> => {
  const data = await request<{ status: string; domains: DomainDto[] }>(
    '/api/domains/all',
    {},
    token,
  );
  return data.domains;
};

export const createDomain = async (
  payload: CreateDomainPayload,
  token?: string | null,
): Promise<string> => {
  const data = await request<{ status: string; message: string; domainId: string }>(
    '/api/domains',
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );
  return data.domainId;
};

export const deactivateDomain = async (id: string, token?: string | null): Promise<void> => {
  await request<{ status: string; message: string; domainId: string }>(
    `/api/domains/${id}/deactivate`,
    { method: 'POST' },
    token,
  );
};

export const reactivateDomain = async (id: string, token?: string | null): Promise<void> => {
  await request<{ status: string; message: string; domainId: string }>(
    `/api/domains/${id}/reactivate`,
    { method: 'POST' },
    token,
  );
};
