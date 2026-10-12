import type { ApiClient } from './client.ts';
import type {
  KbArticle,
  KbArticleRequest,
  KbArticleSummary,
  KbVisibility,
  Page,
  TicketCategory,
} from './types.ts';

export interface KbSearchParams {
  q?: string;
  category?: TicketCategory;
  /** Staff only; customers always get PUBLIC articles. */
  visibility?: KbVisibility;
  page?: number;
  size?: number;
}

/** Knowledge base (docs/adr/0016-knowledge-base.md): everyone reads, staff write, managers delete. */
export function kbApi(client: ApiClient) {
  return {
    search: (params: KbSearchParams = {}): Promise<Page<KbArticleSummary>> => {
      const query = new URLSearchParams();
      if (params.q) query.set('q', params.q);
      if (params.category) query.set('category', params.category);
      if (params.visibility) query.set('visibility', params.visibility);
      if (params.page) query.set('page', String(params.page));
      if (params.size) query.set('size', String(params.size));
      const suffix = query.size > 0 ? `?${query.toString()}` : '';
      return client.get(`/api/kb/articles${suffix}`);
    },
    get: (id: number): Promise<KbArticle> => client.get(`/api/kb/articles/${id}`),
    create: (request: KbArticleRequest): Promise<KbArticle> => client.post('/api/kb/articles', request),
    update: (id: number, request: KbArticleRequest): Promise<KbArticle> =>
      client.put(`/api/kb/articles/${id}`, request),
    remove: (id: number): Promise<void> => client.delete(`/api/kb/articles/${id}`),
    vote: (id: number, helpful: boolean): Promise<KbArticle> =>
      client.put(`/api/kb/articles/${id}/vote`, { helpful }),
  };
}

export type KbApi = ReturnType<typeof kbApi>;
