import type { ProblemDetail } from './types.ts';

/** An error response from the API, carrying the backend's ProblemDetail. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`);
    this.name = 'ApiError';
    this.status = problem.status;
    this.code = problem.code;
    this.fieldErrors = problem.errors ?? [];
  }
}

/** Returns a valid access token; with {@code forceRefresh} it must refresh first. */
export type TokenSource = (forceRefresh?: boolean) => Promise<string>;

export interface ApiClient {
  get<T>(path: string): Promise<T>;
  post<T>(path: string, body?: unknown): Promise<T>;
  delete(path: string): Promise<void>;
  /** multipart/form-data; the browser sets the boundary header. */
  upload<T>(path: string, form: FormData): Promise<T>;
  /** Binary response, e.g. an attachment. */
  download(path: string): Promise<Blob>;
}

type Body = { kind: 'none' } | { kind: 'json'; value: unknown } | { kind: 'form'; value: FormData };

/**
 * Thin fetch wrapper for the same-origin /api. Adds the bearer token, parses JSON and turns error
 * responses into {@link ApiError}. A 401 is retried once with a freshly refreshed token.
 */
export function createApiClient(getToken: TokenSource, fetchImpl: typeof fetch = fetch): ApiClient {
  async function send(method: string, path: string, body: Body, retried = false): Promise<Response> {
    const token = await getToken(retried);
    const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
    let payload: BodyInit | undefined;
    if (body.kind === 'json') {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body.value);
    } else if (body.kind === 'form') {
      payload = body.value;
    }
    const response = await fetchImpl(path, { method, headers, body: payload });

    if (response.status === 401 && !retried) {
      return send(method, path, body, true);
    }
    if (!response.ok) {
      throw new ApiError(await readProblem(response));
    }
    return response;
  }

  async function json<T>(response: Response): Promise<T> {
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
  }

  return {
    get: async (path) => json(await send('GET', path, { kind: 'none' })),
    post: async (path, body) =>
      json(await send('POST', path, body === undefined ? { kind: 'none' } : { kind: 'json', value: body })),
    delete: async (path) => {
      await send('DELETE', path, { kind: 'none' });
    },
    upload: async (path, form) => json(await send('POST', path, { kind: 'form', value: form })),
    download: async (path) => (await send('GET', path, { kind: 'none' })).blob(),
  };
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    return { ...problem, status: problem.status ?? response.status };
  } catch {
    return { status: response.status, title: response.statusText };
  }
}
