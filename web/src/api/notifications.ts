import type { ApiClient } from './client.ts';
import type { AppNotification, Page } from './types.ts';

export function notificationsApi(client: ApiClient) {
  return {
    list(params: { unreadOnly?: boolean; size?: number } = {}): Promise<Page<AppNotification>> {
      const query = new URLSearchParams();
      if (params.unreadOnly) query.set('unreadOnly', 'true');
      if (params.size !== undefined) query.set('size', String(params.size));
      const suffix = query.size > 0 ? `?${query}` : '';
      return client.get(`/api/notifications${suffix}`);
    },
    unreadCount: (): Promise<{ count: number }> => client.get('/api/notifications/unread-count'),
    markRead: (id: number): Promise<void> => client.post(`/api/notifications/${id}/read`),
    markAllRead: (): Promise<{ updated: number }> => client.post('/api/notifications/read-all'),
  };
}

export type NotificationsApi = ReturnType<typeof notificationsApi>;
