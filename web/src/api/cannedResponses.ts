import type { ApiClient } from './client.ts';
import type { CannedResponse, CannedResponseRequest } from './types.ts';

/** Reply templates: staff read them, managers change them. */
export function cannedResponsesApi(client: ApiClient) {
  return {
    list: (): Promise<CannedResponse[]> => client.get('/api/canned-responses'),
    create: (request: CannedResponseRequest): Promise<CannedResponse> => client.post('/api/canned-responses', request),
    update: (id: number, request: CannedResponseRequest): Promise<CannedResponse> =>
      client.put(`/api/canned-responses/${id}`, request),
    remove: (id: number): Promise<void> => client.delete(`/api/canned-responses/${id}`),
  };
}

export type CannedResponsesApi = ReturnType<typeof cannedResponsesApi>;
