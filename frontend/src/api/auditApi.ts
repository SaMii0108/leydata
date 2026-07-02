const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export { ApiError } from './usersApi';
import { ApiError } from './usersApi';

export interface AuditLogDto {
  id: string;
  tableName: string;
  recordId: string;
  action: string;
  oldData: string | null;
  newData: string | null;
  actorId: string;
  actorRole: string;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
  logHash: string | null;
}

export interface AuditLogsResponse {
  status: string;
  logs: AuditLogDto[];
  total: number;
  page: number;
  totalPages: number;
}

export interface AuditLogFilters {
  action?: string;
  table?: string;
  actorEmail?: string;
  page?: number;
  size?: number;
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

export const getAuditLogs = (
  filters?: AuditLogFilters,
  token?: string | null,
): Promise<AuditLogsResponse> => {
  const params = new URLSearchParams();
  if (filters?.action)             params.set('action', filters.action);
  if (filters?.table)              params.set('table', filters.table);
  if (filters?.actorEmail)         params.set('actorEmail', filters.actorEmail);
  if (filters?.page !== undefined) params.set('page', String(filters.page));
  if (filters?.size !== undefined) params.set('size', String(filters.size));
  const q = params.toString() ? `?${params.toString()}` : '';
  return request<AuditLogsResponse>(`/api/audit/logs${q}`, {}, token);
};
