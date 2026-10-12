import type { ApiClient } from './client.ts';
import type {
  Activity,
  Attachment,
  Comment,
  CommentVisibility,
  CreateCommentRequest,
  CreateTicketRequest,
  Page,
  RateTicketRequest,
  StaffMember,
  StatusHistoryEntry,
  Ticket,
  TicketCategory,
  TicketScope,
  TicketStatus,
  TransitionRequest,
  UpdateTicketRequest,
} from './types.ts';

export interface TicketListParams {
  /** Title text, or a ticket id such as 12 or #12. */
  q?: string;
  scope?: TicketScope;
  category?: TicketCategory;
  status?: TicketStatus;
  /** Staff work queue: me, unassigned or a user id. */
  assignee?: string;
  /** Most urgent SLA first instead of newest first. */
  sort?: 'SLA';
  page?: number;
  size?: number;
}

export function ticketsApi(client: ApiClient) {
  return {
    list(params: TicketListParams = {}): Promise<Page<Ticket>> {
      const query = new URLSearchParams();
      if (params.q) query.set('q', params.q);
      if (params.scope) query.set('scope', params.scope);
      if (params.category) query.set('category', params.category);
      if (params.status) query.set('status', params.status);
      if (params.assignee) query.set('assignee', params.assignee);
      if (params.sort) query.set('sort', params.sort);
      if (params.page !== undefined) query.set('page', String(params.page));
      if (params.size !== undefined) query.set('size', String(params.size));
      const suffix = query.size > 0 ? `?${query}` : '';
      return client.get(`/api/tickets${suffix}`);
    },
    get: (id: number): Promise<Ticket> => client.get(`/api/tickets/${id}`),
    create: (request: CreateTicketRequest): Promise<Ticket> => client.post('/api/tickets', request),
    transition: (id: number, request: TransitionRequest): Promise<Ticket> =>
      client.post(`/api/tickets/${id}/transitions`, request),
    assign: (id: number, assigneeId: string | null): Promise<Ticket> =>
      client.put(`/api/tickets/${id}/assignee`, { assigneeId }),
    update: (id: number, request: UpdateTicketRequest): Promise<Ticket> => client.patch(`/api/tickets/${id}`, request),
    activities: (id: number): Promise<Activity[]> => client.get(`/api/tickets/${id}/activities`),
    /** Agents and managers that can be assigned (signed in at least once). */
    staff: (): Promise<StaffMember[]> => client.get('/api/users/staff'),
    rate: (id: number, request: RateTicketRequest): Promise<Ticket> => client.post(`/api/tickets/${id}/rating`, request),
    history: (id: number): Promise<StatusHistoryEntry[]> => client.get(`/api/tickets/${id}/history`),
    comments: (id: number): Promise<Comment[]> => client.get(`/api/tickets/${id}/comments`),
    addComment: (id: number, request: CreateCommentRequest): Promise<Comment> =>
      client.post(`/api/tickets/${id}/comments`, request),
    attachments: (id: number): Promise<Attachment[]> => client.get(`/api/tickets/${id}/attachments`),
    uploadAttachment(id: number, file: File, visibility: CommentVisibility): Promise<Attachment> {
      const form = new FormData();
      form.append('file', file);
      form.append('visibility', visibility);
      return client.upload(`/api/tickets/${id}/attachments`, form);
    },
    downloadAttachment: (id: number, attachmentId: number): Promise<Blob> =>
      client.download(`/api/tickets/${id}/attachments/${attachmentId}/content`),
    deleteAttachment: (id: number, attachmentId: number): Promise<void> =>
      client.delete(`/api/tickets/${id}/attachments/${attachmentId}`),
  };
}

export type TicketsApi = ReturnType<typeof ticketsApi>;
