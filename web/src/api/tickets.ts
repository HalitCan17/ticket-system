import type { ApiClient } from './client.ts';
import type {
  Comment,
  CreateCommentRequest,
  CreateTicketRequest,
  Page,
  StatusHistoryEntry,
  Ticket,
  TicketStatus,
  TransitionRequest,
} from './types.ts';

export interface TicketListParams {
  status?: TicketStatus;
  page?: number;
  size?: number;
}

export function ticketsApi(client: ApiClient) {
  return {
    list(params: TicketListParams = {}): Promise<Page<Ticket>> {
      const query = new URLSearchParams();
      if (params.status) query.set('status', params.status);
      if (params.page !== undefined) query.set('page', String(params.page));
      if (params.size !== undefined) query.set('size', String(params.size));
      const suffix = query.size > 0 ? `?${query}` : '';
      return client.get(`/api/tickets${suffix}`);
    },
    get: (id: number): Promise<Ticket> => client.get(`/api/tickets/${id}`),
    create: (request: CreateTicketRequest): Promise<Ticket> => client.post('/api/tickets', request),
    transition: (id: number, request: TransitionRequest): Promise<Ticket> =>
      client.post(`/api/tickets/${id}/transitions`, request),
    history: (id: number): Promise<StatusHistoryEntry[]> => client.get(`/api/tickets/${id}/history`),
    comments: (id: number): Promise<Comment[]> => client.get(`/api/tickets/${id}/comments`),
    addComment: (id: number, request: CreateCommentRequest): Promise<Comment> =>
      client.post(`/api/tickets/${id}/comments`, request),
  };
}

export type TicketsApi = ReturnType<typeof ticketsApi>;
