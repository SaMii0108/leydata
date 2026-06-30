const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export interface LegalBasisDto {
  id: string;
  code: string;
  name: string;
  description: string;
  consentRequired: boolean;
  isActive: boolean;
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

export const getLegalBasis = async (token?: string | null): Promise<LegalBasisDto[]> => {
  return request<LegalBasisDto[]>('/api/legal-basis', {}, token);
};

export const getConsentLegalBasis = async (token?: string | null): Promise<LegalBasisDto[]> => {
  return request<LegalBasisDto[]>('/api/legal-basis/consent', {}, token);
};
