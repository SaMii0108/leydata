const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export { ApiError } from './usersApi';
import { ApiError } from './usersApi';

export type DataUseType =
  | 'STORAGE'
  | 'PROCESSING'
  | 'TRANSFER_TO_THIRD_PARTIES'
  | 'PROFILING'
  | 'ANALYSIS';

export const DATA_USE_LABELS: Record<DataUseType, string> = {
  STORAGE: 'Almacenamiento',
  PROCESSING: 'Procesamiento',
  TRANSFER_TO_THIRD_PARTIES: 'Transferencia a terceros',
  PROFILING: 'Elaboración de perfiles',
  ANALYSIS: 'Análisis',
};

export const ALL_DATA_USES: DataUseType[] = [
  'STORAGE',
  'PROCESSING',
  'TRANSFER_TO_THIRD_PARTIES',
  'PROFILING',
  'ANALYSIS',
];

export const RETENTION_UNIT_LABELS: Record<string, string> = {
  DAYS: 'Días',
  MONTHS: 'Meses',
  YEARS: 'Años',
};

export interface DataCategoryResponse {
  id: string;
  code: string;
  name: string;
  description: string | null;
  isSensitive: boolean;
  isSystem: boolean;
  isActive: boolean;
  createdAt: string;
}

export interface DataRetentionPolicyRequest {
  retentionPeriod: number;
  retentionUnit: 'DAYS' | 'MONTHS' | 'YEARS';
  legalJustification?: string;
  anonymizeAfter: boolean;
}

export interface PurposeDataCategoryRequest {
  dataCategoryId: string;
  required: boolean;
  dataUses: DataUseType[];
  retention: DataRetentionPolicyRequest;
}

export interface PurposeDataCategoryResponse {
  id: string;
  purposeId: string;
  dataCategoryId: string;
  dataCategoryCode: string;
  dataCategoryName: string;
  isSensitive: boolean;
  required: boolean;
  dataUses: DataUseType[];
  retentionPolicyId: string | null;
  retentionPeriod: number | null;
  retentionUnit: string | null;
  legalJustification: string | null;
  anonymizeAfter: boolean | null;
  retentionLocked: boolean;
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

export const getDataCategories = async (
  token?: string | null,
): Promise<DataCategoryResponse[]> =>
  request<DataCategoryResponse[]>('/api/data-categories', {}, token);

export const getPurposeDataCategories = async (
  purposeId: string,
  token?: string | null,
): Promise<PurposeDataCategoryResponse[]> =>
  request<PurposeDataCategoryResponse[]>(
    `/api/purposes/${purposeId}/data-categories`,
    {},
    token,
  );

export const linkPurposeDataCategory = async (
  purposeId: string,
  payload: PurposeDataCategoryRequest,
  token?: string | null,
): Promise<PurposeDataCategoryResponse> =>
  request<PurposeDataCategoryResponse>(
    `/api/purposes/${purposeId}/data-categories`,
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );

export const updatePurposeDataCategoryRetention = async (
  purposeId: string,
  pdcId: string,
  payload: DataRetentionPolicyRequest,
  token?: string | null,
): Promise<PurposeDataCategoryResponse> =>
  request<PurposeDataCategoryResponse>(
    `/api/purposes/${purposeId}/data-categories/${pdcId}/retention`,
    { method: 'PUT', body: JSON.stringify(payload) },
    token,
  );

export const unlinkPurposeDataCategory = async (
  purposeId: string,
  pdcId: string,
  token?: string | null,
): Promise<void> =>
  requestVoid(
    `/api/purposes/${purposeId}/data-categories/${pdcId}`,
    { method: 'DELETE' },
    token,
  );
