const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export type NotificationType =
  | 'PURPOSE_APPROVED'
  | 'PURPOSE_REJECTED'
  | 'DOCUMENT_PUBLISHED'
  | 'PURPOSE_REQUEST_FULFILLED';

export interface NotificationDto {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  referenceId: string | null;
  read: boolean;
  createdAt: string;
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
    throw new Error(message);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const getNotifications = (token?: string | null): Promise<NotificationDto[]> =>
  request<NotificationDto[]>('/api/notifications', {}, token);

export const getUnreadCount = (token?: string | null): Promise<number> =>
  request<{ count: number }>('/api/notifications/unread-count', {}, token)
    .then((data) => data.count);

export const markAsRead = (id: string, token?: string | null): Promise<NotificationDto> =>
  request<NotificationDto>(`/api/notifications/${id}/read`, { method: 'PATCH' }, token);

export const markAllAsRead = (token?: string | null): Promise<void> =>
  request<void>('/api/notifications/read-all', { method: 'PATCH' }, token);
