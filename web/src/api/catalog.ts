import type { ApiClient } from './client.ts';
import type { CatalogItem, CatalogItemRequest, Page, ServiceRequest, ServiceRequestStatus } from './types.ts';

export interface ServiceRequestListParams {
  /** Staff only: just the caller's own requests. Customers always get their own. */
  mine?: boolean;
  status?: ServiceRequestStatus;
  page?: number;
  size?: number;
}

/** Service catalog and requests (docs/adr/0017-service-catalog.md). */
export function catalogApi(client: ApiClient) {
  return {
    items: (): Promise<CatalogItem[]> => client.get('/api/catalog/items'),
    item: (id: number): Promise<CatalogItem> => client.get(`/api/catalog/items/${id}`),
    createItem: (request: CatalogItemRequest): Promise<CatalogItem> => client.post('/api/catalog/items', request),
    updateItem: (id: number, request: CatalogItemRequest): Promise<CatalogItem> =>
      client.put(`/api/catalog/items/${id}`, request),
    requests: (params: ServiceRequestListParams = {}): Promise<Page<ServiceRequest>> => {
      const query = new URLSearchParams();
      if (params.mine) query.set('mine', 'true');
      if (params.status) query.set('status', params.status);
      if (params.page) query.set('page', String(params.page));
      if (params.size) query.set('size', String(params.size));
      const suffix = query.size > 0 ? `?${query.toString()}` : '';
      return client.get(`/api/service-requests${suffix}`);
    },
    request: (id: number): Promise<ServiceRequest> => client.get(`/api/service-requests/${id}`),
    create: (itemId: number, answers: Record<string, string>): Promise<ServiceRequest> =>
      client.post('/api/service-requests', { itemId, answers }),
    cancel: (id: number): Promise<ServiceRequest> => client.post(`/api/service-requests/${id}/cancel`, {}),
    decide: (id: number, approved: boolean, comment?: string): Promise<ServiceRequest> =>
      client.post(`/api/service-requests/${id}/decision`, { approved, comment: comment ?? null }),
    pendingCount: (): Promise<{ count: number }> => client.get('/api/service-requests/pending-count'),
  };
}

export type CatalogApi = ReturnType<typeof catalogApi>;
