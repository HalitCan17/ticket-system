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

/** First response SLA: the first customer-visible staff reaction (external comment or resolution). */
export interface FirstResponse {
  dueAt: string;
  respondedAt: string | null;
  breached: boolean;
  /** While awaiting a response; negative once late. Null after the response or when closed. */
  remainingSeconds: number | null;
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
  /** Support group handling the ticket; null = none. */
  groupId: number | null;
  groupName: string | null;
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
  firstResponse: FirstResponse | null;
  /** Set when the breached resolution SLA escalated the ticket. */
  escalatedAt: string | null;
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
  groupId?: number;
  removeGroup?: boolean;
}

export type ActivityType = 'ASSIGNEE_CHANGED' | 'PRIORITY_CHANGED' | 'CATEGORY_CHANGED' | 'GROUP_CHANGED';

/** Staff-only log of assignment, priority and category changes. */
export interface Activity {
  id: number;
  type: ActivityType;
  /** User id (ASSIGNEE_CHANGED), group id (GROUP_CHANGED) or the enum name; null = none. */
  oldValue: string | null;
  oldName: string | null;
  newValue: string | null;
  newName: string | null;
  note: string | null;
  actorId: string;
  actorName: string | null;
  createdAt: string;
}

/** A team-wide reply template; placeholders are filled in when inserting. */
export interface CannedResponse {
  id: number;
  title: string;
  body: string;
  /** Suggested visibility for a comment written from it. */
  visibility: CommentVisibility;
  updatedBy: string;
  updatedAt: string;
}

export interface CannedResponseRequest {
  title: string;
  body: string;
  visibility: CommentVisibility;
}

/** Manager summary; counts per status/category keyed by the enum name. */
export interface ReportSummary {
  days: number;
  from: string;
  to: string;
  statusCounts: Record<string, number>;
  openTickets: number;
  unassignedOpen: number;
  breachedOpen: number;
  created: number;
  resolution: { resolved: number; withinSla: number; withinSlaRate: number | null; avgResolveSeconds: number | null };
  /** distribution[0] = number of 1-star ratings */
  satisfaction: { count: number; average: number | null; distribution: number[] };
  categories: Record<string, number>;
  firstResponse: { responded: number; withinTarget: number; withinTargetRate: number | null };
  escalatedOpen: number;
}

export interface TeamMember {
  id: string;
  name: string;
  role: Role | null;
  openWork: number;
  breachedOpen: number;
  resolved: number;
  ratings: number;
  averageRating: number | null;
}

export type WeekDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface Holiday {
  /** yyyy-mm-dd */
  date: string;
  name: string;
}

/** Business hours the SLA clocks run in, plus the (read-only) SLA targets they apply to. */
export interface SlaCalendar {
  enabled: boolean;
  zoneId: string;
  workDays: WeekDay[];
  /** HH:mm */
  start: string;
  end: string;
  holidays: Holiday[];
  resolutionSeconds: Record<TicketPriority, number>;
  firstResponseSeconds: Record<TicketPriority, number>;
  raisePriorityOnBreach: boolean;
}

export interface SlaCalendarRequest {
  enabled: boolean;
  zoneId: string;
  workDays: WeekDay[];
  start: string;
  end: string;
  holidays: Holiday[];
}

export interface SlaCalendarUpdate {
  calendar: SlaCalendar;
  /** Open tickets whose due and warning moments were recalculated. */
  recalculatedTickets: number;
}

export interface StaffMember {
  id: string;
  name: string;
  role: Role | null;
}

export type AssignmentMode = 'NONE' | 'ROUND_ROBIN' | 'LEAST_LOADED';

export interface SupportGroup {
  id: number;
  name: string;
  description: string | null;
  assignmentMode: AssignmentMode;
  /** Categories routed to this group. */
  categories: TicketCategory[];
  members: StaffMember[];
}

/** Result of replacing a group's members. */
export interface MembersResult {
  group: SupportGroup;
  /** Open tickets taken from removed members and assigned again (or put back in the group's queue). */
  releasedTickets: number;
}

export interface GroupRequest {
  name: string;
  description?: string;
  assignmentMode: AssignmentMode;
}

/** Open work of a group now; id null = tickets without a group. */
export interface GroupWorkload {
  id: number | null;
  name: string | null;
  members: number;
  openWork: number;
  unassignedOpen: number;
  breachedOpen: number;
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
  | 'PRIORITY_CHANGED'
  | 'SLA_WARNING'
  | 'FIRST_RESPONSE_BREACHED';

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
