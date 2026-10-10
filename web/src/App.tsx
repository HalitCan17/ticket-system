import { useMemo, useState, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Navigate, type RouteObject } from 'react-router';
import { ApiContext } from './api/ApiContext.tsx';
import { ApiError, createApiClient } from './api/client.ts';
import { ticketsApi } from './api/tickets.ts';
import { useAuth } from './auth/AuthContext.tsx';
import { Layout } from './components/Layout.tsx';
import { NewTicketPage } from './pages/NewTicketPage.tsx';
import { NotFoundPage } from './pages/NotFoundPage.tsx';
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
  const api = useMemo(() => ticketsApi(createApiClient(getToken)), [getToken]);
  const [queryClient] = useState(createQueryClient);

  return (
    <ApiContext.Provider value={api}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </ApiContext.Provider>
  );
}
