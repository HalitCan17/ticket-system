// Mirrors the backend DTOs in backend/src/main/java/com/itsm/backend/ticket/dto.

export type Role = 'CUSTOMER' | 'AGENT' | 'MANAGER';

export type TicketStatus = 'NEW' | 'IN_PROGRESS' | 'WAITING_FOR_CUSTOMER' | 'RESOLVED' | 'CLOSED';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type TicketCategory = 'HARDWARE' | 'SOFTWARE' | 'NETWORK' | 'ACCESS' | 'OTHER';

/** List tabs: OPEN = everything except CLOSED. */
export type TicketScope = 'OPEN' | 'CLOSED' | 'ALL';

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

export interface Rating {
  score: number;
  comment: string | null;
  ratedAt: string;
}

export interface Ticket {
  id: number;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  category: TicketCategory;
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
  /** The requester's satisfaction rating; null until rated. */
  rating: Rating | null;
  /** Whether the caller may rate now (requester, resolved, not yet rated). */
  canRate: boolean;
  /** Staff may take this unassigned ticket. */
  canClaim: boolean;
  /** Caller may give the ticket to someone else or unassign it (manager, or agent on own ticket). */
  canReassign: boolean;
  /** Staff may change priority and category. */
  canEdit: boolean;
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
  category: TicketCategory;
}

export interface UpdateTicketRequest {
  priority?: TicketPriority;
  category?: TicketCategory;
  /** Required when the priority changes. */
  reason?: string;
}

export type ActivityType = 'ASSIGNEE_CHANGED' | 'PRIORITY_CHANGED' | 'CATEGORY_CHANGED';

/** Staff-only log of assignment, priority and category changes. */
export interface Activity {
  id: number;
  type: ActivityType;
  /** User id for ASSIGNEE_CHANGED (null = unassigned), otherwise the enum name. */
  oldValue: string | null;
  oldName: string | null;
  newValue: string | null;
  newName: string | null;
  note: string | null;
  actorId: string;
  actorName: string | null;
  createdAt: string;
}

export interface StaffMember {
  id: string;
  name: string;
  role: Role;
}

export interface RateTicketRequest {
  score: number;
  comment?: string;
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
  | 'SLA_BREACHED'
  | 'ATTACHMENT_ADDED'
  | 'TICKET_RATED'
  | 'TICKET_ASSIGNED'
  | 'TICKET_UNASSIGNED'
  | 'PRIORITY_CHANGED';

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

export interface Attachment {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  visibility: CommentVisibility;
  uploaderId: string;
  uploaderName: string | null;
  createdAt: string;
  /** Uploader or manager, ticket not closed. */
  canDelete: boolean;
}
