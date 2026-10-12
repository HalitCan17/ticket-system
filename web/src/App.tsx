import { useMemo, useState, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Navigate, type RouteObject } from 'react-router';
import { ApiContext } from './api/ApiContext.tsx';
import { cannedResponsesApi } from './api/cannedResponses.ts';
import { ApiError, createApiClient } from './api/client.ts';
import { groupsApi } from './api/groups.ts';
import { notificationsApi } from './api/notifications.ts';
import { reportsApi } from './api/reports.ts';
import { kbApi } from './api/kb.ts';
import { slaCalendarApi } from './api/slaCalendar.ts';
import { ticketsApi } from './api/tickets.ts';
import { useAuth } from './auth/AuthContext.tsx';
import { Layout } from './components/Layout.tsx';
import { CannedResponsesPage } from './pages/CannedResponsesPage.tsx';
import { DashboardPage } from './pages/DashboardPage.tsx';
import { GroupsPage } from './pages/GroupsPage.tsx';
import { NewTicketPage } from './pages/NewTicketPage.tsx';
import { NotFoundPage } from './pages/NotFoundPage.tsx';
import { KbArticlePage } from './pages/KbArticlePage.tsx';
import { KbEditPage } from './pages/KbEditPage.tsx';
import { KbListPage } from './pages/KbListPage.tsx';
import { SlaSettingsPage } from './pages/SlaSettingsPage.tsx';
import { TicketDetailPage } from './pages/TicketDetailPage.tsx';
import { TicketListPage } from './pages/TicketListPage.tsx';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/tickets" replace /> },
      { path: 'tickets', element: <TicketListPage /> },
      { path: 'tickets/new', element: <NewTicketPage /> },
      { path: 'tickets/:id', element: <TicketDetailPage /> },
      { path: 'canned-responses', element: <CannedResponsesPage /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'groups', element: <GroupsPage /> },
      { path: 'sla', element: <SlaSettingsPage /> },
      { path: 'kb', element: <KbListPage /> },
      { path: 'kb/new', element: <KbEditPage /> },
      { path: 'kb/:id', element: <KbArticlePage /> },
      { path: 'kb/:id/edit', element: <KbEditPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // 4xx answers (not found, forbidden, validation) will not change on retry.
        retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 2,
        refetchOnWindowFocus: false,
      },
    },
  });
}

/** API client and query cache for the signed-in user (must be inside the AuthGate). */
export function AppProviders({ children }: { children: ReactNode }) {
  const { getToken } = useAuth();
  const api = useMemo(() => {
    const client = createApiClient(getToken);
    return {
      tickets: ticketsApi(client),
      notifications: notificationsApi(client),
      cannedResponses: cannedResponsesApi(client),
      reports: reportsApi(client),
      groups: groupsApi(client),
      slaCalendar: slaCalendarApi(client),
      kb: kbApi(client),
    };
  }, [getToken]);
  const [queryClient] = useState(createQueryClient);

  return (
    <ApiContext.Provider value={api}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </ApiContext.Provider>
  );
}
