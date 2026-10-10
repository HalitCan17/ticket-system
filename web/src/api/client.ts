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
}

/**
 * Thin fetch wrapper for the same-origin /api. Adds the bearer token, parses JSON and turns error
 * responses into {@link ApiError}. A 401 is retried once with a freshly refreshed token.
 */
export function createApiClient(getToken: TokenSource, fetchImpl: typeof fetch = fetch): ApiClient {
  async function request<T>(method: string, path: string, body: unknown, retried = false): Promise<T> {
    const token = await getToken(retried);
    const headers: Record<string, string> = { Authorization: `Bearer ${token}`, Accept: 'application/json' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    const response = await fetchImpl(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (response.status === 401 && !retried) {
      return request<T>(method, path, body, true);
    }
    if (!response.ok) {
      throw new ApiError(await readProblem(response));
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  return {
    get: (path) => request('GET', path, undefined),
    post: (path, body) => request('POST', path, body),
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
