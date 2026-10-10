import { render } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { createQueryClient, routes } from '../App.tsx';
import { ApiContext } from '../api/ApiContext.tsx';
import type { NotificationsApi } from '../api/notifications.ts';
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
    history: vi.fn().mockResolvedValue([]),
    comments: vi.fn().mockResolvedValue([]),
    addComment: vi.fn(),
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

/** Renders the real route tree with a fake signed-in user and API. */
export function renderApp(
  path: string,
  options: { role?: Role; api?: TicketsApi; notifications?: NotificationsApi } = {},
) {
  const auth: Auth = {
    user: TEST_USERS[options.role ?? 'CUSTOMER'],
    getToken: vi.fn().mockResolvedValue('token'),
    logout: vi.fn(),
  };
  const api = options.api ?? fakeApi();
  const notifications = options.notifications ?? fakeNotificationsApi();
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({ queries: { retry: false } });

  const result = render(
    <AuthContext.Provider value={auth}>
      <ApiContext.Provider value={{ tickets: api, notifications }}>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </ApiContext.Provider>
    </AuthContext.Provider>,
  );
  return { ...result, auth, api, notifications, router };
}
