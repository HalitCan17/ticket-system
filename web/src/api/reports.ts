import type { ApiClient } from './client.ts';
import type { GroupWorkload, ReportSummary, TeamMember } from './types.ts';

/** Manager-only figures (docs/adr/0013-manager-dashboard.md). */
export function reportsApi(client: ApiClient) {
  return {
    summary: (days: number): Promise<ReportSummary> => client.get(`/api/reports/summary?days=${days}`),
    groups: (): Promise<GroupWorkload[]> => client.get('/api/reports/groups'),
    team: (days: number): Promise<TeamMember[]> => client.get(`/api/reports/team?days=${days}`),
  };
}

export type ReportsApi = ReturnType<typeof reportsApi>;
