import { render } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { createQueryClient, routes } from '../App.tsx';
import { ApiContext } from '../api/ApiContext.tsx';
import type { CannedResponsesApi } from '../api/cannedResponses.ts';
import type { GroupsApi } from '../api/groups.ts';
import type { NotificationsApi } from '../api/notifications.ts';
import type { ReportsApi } from '../api/reports.ts';
import type { SlaCalendarApi } from '../api/slaCalendar.ts';
import type { TicketsApi } from '../api/tickets.ts';
import type { Role } from '../api/types.ts';
import { AuthContext, type Auth } from '../auth/AuthContext.tsx';

export const TEST_USERS: Record<Role, Auth['user']> = {
  CUSTOMER: { id: 'cust-1', username: 'customer1', displayName: 'Customer One', role: 'CUSTOMER' },
  AGENT: { id: 'agent-1', username: 'agent1', displayName: 'Agent One', role: 'AGENT' },
  MANAGER: { id: 'manager-1', username: 'manager1', displayName: 'Manager One', role: 'MANAGER' },
};

/** A TicketsApi whose methods are vi.fn(); override the ones a test needs. */
export function fakeApi(overrides: Partial<TicketsApi> = {}): TicketsApi {
  return {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    transition: vi.fn(),
    rate: vi.fn(),
    assign: vi.fn(),
    update: vi.fn(),
    activities: vi.fn().mockResolvedValue([]),
    staff: vi.fn().mockResolvedValue([]),
    history: vi.fn().mockResolvedValue([]),
    comments: vi.fn().mockResolvedValue([]),
    addComment: vi.fn(),
    attachments: vi.fn().mockResolvedValue([]),
    uploadAttachment: vi.fn(),
    downloadAttachment: vi.fn(),
    deleteAttachment: vi.fn(),
    ...overrides,
  };
}

/** A NotificationsApi with no notifications; override what a test needs. */
export function fakeNotificationsApi(overrides: Partial<NotificationsApi> = {}): NotificationsApi {
  return {
    list: vi.fn().mockResolvedValue({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }),
    unreadCount: vi.fn().mockResolvedValue({ count: 0 }),
    markRead: vi.fn().mockResolvedValue(undefined),
    markAllRead: vi.fn().mockResolvedValue({ updated: 0 }),
    ...overrides,
  };
}

/** A CannedResponsesApi with no templates; override what a test needs. */
export function fakeCannedResponsesApi(overrides: Partial<CannedResponsesApi> = {}): CannedResponsesApi {
  return {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

/** A ReportsApi without data; override what a test needs. */
export function fakeReportsApi(overrides: Partial<ReportsApi> = {}): ReportsApi {
  return { summary: vi.fn(), team: vi.fn().mockResolvedValue([]), groups: vi.fn().mockResolvedValue([]), ...overrides };
}

/** A GroupsApi without groups; override what a test needs. */
export function fakeGroupsApi(overrides: Partial<GroupsApi> = {}): GroupsApi {
  return {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn().mockResolvedValue(undefined),
    setMembers: vi.fn(),
    setRoutes: vi.fn(),
    ...overrides,
  };
}

/** A SlaCalendarApi without data; override what a test needs. */
export function fakeSlaCalendarApi(overrides: Partial<SlaCalendarApi> = {}): SlaCalendarApi {
  return { get: vi.fn(), save: vi.fn(), ...overrides };
}

/** Renders the real route tree with a fake signed-in user and API. */
export function renderApp(
  path: string,
  options: {
    role?: Role;
    api?: TicketsApi;
    notifications?: NotificationsApi;
    canned?: CannedResponsesApi;
    reports?: ReportsApi;
    groups?: GroupsApi;
    slaCalendar?: SlaCalendarApi;
  } = {},
) {
  const auth: Auth = {
    user: TEST_USERS[options.role ?? 'CUSTOMER'],
    getToken: vi.fn().mockResolvedValue('token'),
    logout: vi.fn(),
  };
  const api = options.api ?? fakeApi();
  const notifications = options.notifications ?? fakeNotificationsApi();
  const cannedResponses = options.canned ?? fakeCannedResponsesApi();
  const reports = options.reports ?? fakeReportsApi();
  const groups = options.groups ?? fakeGroupsApi();
  const slaCalendar = options.slaCalendar ?? fakeSlaCalendarApi();
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({ queries: { retry: false } });

  const result = render(
    <AuthContext.Provider value={auth}>
      <ApiContext.Provider value={{ tickets: api, notifications, cannedResponses, reports, groups, slaCalendar }}>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </ApiContext.Provider>
    </AuthContext.Provider>,
  );
  return { ...result, auth, api, notifications, cannedResponses, reports, groups, slaCalendar, router };
}
