import { createContext, useContext } from 'react';
import type { CannedResponsesApi } from './cannedResponses.ts';
import type { GroupsApi } from './groups.ts';
import type { KbApi } from './kb.ts';
import type { NotificationsApi } from './notifications.ts';
import type { ReportsApi } from './reports.ts';
import type { SlaCalendarApi } from './slaCalendar.ts';
import type { TicketsApi } from './tickets.ts';

export interface Api {
  tickets: TicketsApi;
  notifications: NotificationsApi;
  cannedResponses: CannedResponsesApi;
  reports: ReportsApi;
  groups: GroupsApi;
  slaCalendar: SlaCalendarApi;
  kb: KbApi;
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

export function useCannedResponsesApi(): CannedResponsesApi {
  return useApi().cannedResponses;
}

export function useReportsApi(): ReportsApi {
  return useApi().reports;
}

export function useGroupsApi(): GroupsApi {
  return useApi().groups;
}

export function useSlaCalendarApi(): SlaCalendarApi {
  return useApi().slaCalendar;
}

export function useKbApi(): KbApi {
  return useApi().kb;
}
