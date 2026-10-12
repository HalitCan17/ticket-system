import type { ApiClient } from './client.ts';
import type { SlaCalendar, SlaCalendarRequest, SlaCalendarUpdate } from './types.ts';

/** Business hours for the SLA clocks (docs/adr/0015-sla-extensions.md). Staff read, managers change. */
export function slaCalendarApi(client: ApiClient) {
  return {
    get: (): Promise<SlaCalendar> => client.get('/api/sla/calendar'),
    save: (request: SlaCalendarRequest): Promise<SlaCalendarUpdate> => client.put('/api/sla/calendar', request),
  };
}

export type SlaCalendarApi = ReturnType<typeof slaCalendarApi>;
