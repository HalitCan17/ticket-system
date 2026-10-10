import { createContext, useContext } from 'react';
import type { NotificationsApi } from './notifications.ts';
import type { TicketsApi } from './tickets.ts';

export interface Api {
  tickets: TicketsApi;
  notifications: NotificationsApi;
}

export const ApiContext = createContext<Api | null>(null);

function useApi(): Api {
  const api = useContext(ApiContext);
  if (!api) {
    throw new Error('API hooks must be used inside an ApiContext provider');
  }
  return api;
}

export function useTicketsApi(): TicketsApi {
  return useApi().tickets;
}

export function useNotificationsApi(): NotificationsApi {
  return useApi().notifications;
}
