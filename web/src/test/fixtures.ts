import type { Comment, Page, Sla, StatusHistoryEntry, Ticket } from '../api/types.ts';

export function anSla(overrides: Partial<Sla> = {}): Sla {
  return {
    state: 'RUNNING',
    targetSeconds: 8 * 3600,
    elapsedSeconds: 3600,
    remainingSeconds: 7 * 3600,
    dueAt: '2026-10-10T17:00:00Z',
    breached: false,
    ...overrides,
  };
}

export function aTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 1,
    title: 'VPN bağlanmıyor',
    description: 'Evden bağlanamıyorum.',
    status: 'NEW',
    priority: 'HIGH',
    category: 'OTHER',
    requesterId: 'cust-1',
    requesterName: 'Customer One',
    assigneeId: null,
    assigneeName: null,
    resolutionNote: null,
    createdAt: '2026-10-10T09:00:00Z',
    updatedAt: '2026-10-10T09:00:00Z',
    resolvedAt: null,
    closedAt: null,
    allowedTransitions: [],
    sla: anSla(),
    autoCloseAt: null,
    rating: null,
    canRate: false,
    ...overrides,
  };
}

export function aPage<T>(items: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length > 0 ? 1 : 0, ...overrides };
}

export function aHistoryEntry(overrides: Partial<StatusHistoryEntry> = {}): StatusHistoryEntry {
  return {
    fromStatus: null,
    toStatus: 'NEW',
    changedBy: 'cust-1',
    changedByName: 'Customer One',
    note: null,
    changedAt: '2026-10-10T09:00:00Z',
    ...overrides,
  };
}

export function aComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    authorId: 'agent-1',
    authorName: 'Agent One',
    visibility: 'EXTERNAL',
    body: 'Hangi işletim sistemini kullanıyorsunuz?',
    createdAt: '2026-10-10T10:00:00Z',
    ...overrides,
  };
}
