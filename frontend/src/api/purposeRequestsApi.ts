const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export interface PurposeRequestSummary {
  id: string;
  title: string;
  justification: string;
  requestedData: string | null;
  domainId: string;
  domainName: string;
  requesterId: string;
  requesterName: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewerId: string | null;
  reviewerName: string | null;
  reviewNotes: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreatePurposeRequestPayload {
  title: string;
  justification: string;
  requestedData?: string;
  domainId: string;
}

export interface ReviewPayload {
  status: 'APPROVED' | 'REJECTED';
  reviewNotes?: string;
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

export const createPurposeRequest = async (
  payload: CreatePurposeRequestPayload,
  token?: string | null,
): Promise<PurposeRequestSummary> => {
  const data = await request<{ status: string; message: string; request: PurposeRequestSummary }>(
    '/api/purpose-requests',
    { method: 'POST', body: JSON.stringify(payload) },
    token,
  );
  return data.request;
};

export const getMyPurposeRequests = async (
  token?: string | null,
): Promise<PurposeRequestSummary[]> => {
  const data = await request<{ status: string; requests: PurposeRequestSummary[] }>(
    '/api/purpose-requests/my',
    {},
    token,
  );
  return data.requests;
};

export const getPendingPurposeRequests = async (
  token?: string | null,
): Promise<PurposeRequestSummary[]> => {
  const data = await request<{ status: string; requests: PurposeRequestSummary[] }>(
    '/api/purpose-requests/pending',
    {},
    token,
  );
  return data.requests;
};

export const getAllPurposeRequests = async (
  token?: string | null,
): Promise<PurposeRequestSummary[]> => {
  const data = await request<{ status: string; requests: PurposeRequestSummary[] }>(
    '/api/purpose-requests',
    {},
    token,
  );
  return data.requests;
};

export const reviewPurposeRequest = async (
  id: string,
  payload: ReviewPayload,
  token?: string | null,
): Promise<PurposeRequestSummary> => {
  const data = await request<{ status: string; message: string; request: PurposeRequestSummary }>(
    `/api/purpose-requests/${id}/review`,
    { method: 'PATCH', body: JSON.stringify(payload) },
    token,
  );
  return data.request;
};
