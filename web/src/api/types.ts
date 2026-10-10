// Mirrors the backend DTOs in backend/src/main/java/com/itsm/backend/ticket/dto.

export type Role = 'CUSTOMER' | 'AGENT' | 'MANAGER';

export type TicketStatus = 'NEW' | 'IN_PROGRESS' | 'WAITING_FOR_CUSTOMER' | 'RESOLVED' | 'CLOSED';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type CommentVisibility = 'INTERNAL' | 'EXTERNAL';

export type SlaState = 'RUNNING' | 'PAUSED' | 'STOPPED';

export interface Sla {
  state: SlaState;
  targetSeconds: number;
  elapsedSeconds: number;
  /** Negative once the target is exceeded. */
  remainingSeconds: number;
  /** Only while the clock is running. */
  dueAt: string | null;
  breached: boolean;
}

export interface Ticket {
  id: number;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  requesterId: string;
  requesterName: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  resolutionNote: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  allowedTransitions: TicketStatus[];
  sla: Sla;
  autoCloseAt: string | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StatusHistoryEntry {
  fromStatus: TicketStatus | null;
  toStatus: TicketStatus;
  changedBy: string;
  changedByName: string | null;
  note: string | null;
  changedAt: string;
}

export interface Comment {
  id: number;
  authorId: string;
  authorName: string | null;
  visibility: CommentVisibility;
  body: string;
  createdAt: string;
}

export interface CreateTicketRequest {
  title: string;
  description: string;
  priority: TicketPriority;
}

export interface TransitionRequest {
  targetStatus: TicketStatus;
  note?: string;
}

export interface CreateCommentRequest {
  body: string;
  visibility: CommentVisibility;
}

/** RFC 9457 ProblemDetail as returned by the backend's GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}

export type NotificationType =
  | 'STATUS_CHANGED'
  | 'AUTO_CLOSED'
  | 'COMMENT_ADDED'
  | 'INTERNAL_COMMENT_ADDED'
  | 'SLA_BREACHED';

/** Mirrors backend NotificationResponse; the sentence is built in the UI from the type. */
export interface AppNotification {
  id: number;
  type: NotificationType;
  ticketId: number;
  ticketTitle: string | null;
  /** The ticket's status right after a STATUS_CHANGED. */
  ticketStatus: TicketStatus | null;
  actorId: string | null;
  actorName: string | null;
  createdAt: string;
  read: boolean;
}
