import type { ApiClient } from './client.ts';
import type { GroupRequest, SupportGroup, TicketCategory } from './types.ts';

/** Support groups and category routing (docs/adr/0014-support-groups.md). Staff read, managers change. */
export function groupsApi(client: ApiClient) {
  return {
    list: (): Promise<SupportGroup[]> => client.get('/api/groups'),
    create: (request: GroupRequest): Promise<SupportGroup> => client.post('/api/groups', request),
    update: (id: number, request: GroupRequest): Promise<SupportGroup> => client.put(`/api/groups/${id}`, request),
    remove: (id: number): Promise<void> => client.delete(`/api/groups/${id}`),
    setMembers: (id: number, userIds: string[]): Promise<SupportGroup> =>
      client.put(`/api/groups/${id}/members`, { userIds }),
    /** Replaces the whole routing table; a category set to null has no group. */
    setRoutes: (routes: Partial<Record<TicketCategory, number | null>>): Promise<Partial<Record<TicketCategory, number>>> =>
      client.put('/api/groups/routes', routes),
  };
}

export type GroupsApi = ReturnType<typeof groupsApi>;
