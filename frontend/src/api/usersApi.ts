const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export interface UserDomain {
  id: string;
  name: string;
}

export interface UserSummaryDto {
  keycloakId: string;
  email: string;
  name: string;
  active: boolean;
  blocked: boolean;
  roles: string[];
  domains: UserDomain[];
}

export interface CreateUserPayload {
  name: string;
  email: string;
  password: string;
  roleCode: string;
}

export interface UpdateUserPayload {
  name?: string;
  roleCodes?: string[];
  domainIds?: string[];
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

export const getUsers = async (token?: string | null): Promise<UserSummaryDto[]> => {
  const data = await request<{ status: string; users: UserSummaryDto[] }>('/api/users', {}, token);
  return data.users;
};

export const getUser = async (id: string, token?: string | null): Promise<UserSummaryDto> => {
  const data = await request<{ status: string; user: UserSummaryDto }>(`/api/users/${id}`, {}, token);
  return data.user;
};

export const createUser = async (
  payload: CreateUserPayload,
  token?: string | null,
): Promise<string> => {
  const data = await request<{ status: string; message: string; keycloakId: string }>(
    '/api/users',
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );
  return data.keycloakId;
};

export const updateUser = async (
  id: string,
  payload: UpdateUserPayload,
  token?: string | null,
): Promise<UserSummaryDto> => {
  const data = await request<{ status: string; message: string; user: UserSummaryDto }>(
    `/api/users/${id}`,
    { method: 'PUT', body: JSON.stringify(payload) },
    token,
  );
  return data.user;
};

export const blockUser = async (id: string, token?: string | null): Promise<UserSummaryDto> => {
  const data = await request<{ status: string; message: string; user: UserSummaryDto }>(
    `/api/users/${id}/block`,
    { method: 'POST' },
    token,
  );
  return data.user;
};

export const deactivateUser = async (id: string, token?: string | null): Promise<UserSummaryDto> => {
  const data = await request<{ status: string; message: string; user: UserSummaryDto }>(
    `/api/users/${id}/deactivate`,
    { method: 'POST' },
    token,
  );
  return data.user;
};

export const reactivateUser = async (id: string, token?: string | null): Promise<UserSummaryDto> => {
  const data = await request<{ status: string; message: string; user: UserSummaryDto }>(
    `/api/users/${id}/reactivate`,
    { method: 'POST' },
    token,
  );
  return data.user;
};
