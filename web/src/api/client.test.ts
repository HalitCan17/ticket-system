import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './client.ts';

function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

describe('createApiClient', () => {
  it('sends the bearer token and parses JSON', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, { id: 1 }));
    const client = createApiClient(vi.fn().mockResolvedValue('abc'), fetchImpl);

    await expect(client.get('/api/tickets/1')).resolves.toEqual({ id: 1 });

    const [path, init] = fetchImpl.mock.calls[0]!;
    expect(path).toBe('/api/tickets/1');
    expect(init.headers.Authorization).toBe('Bearer abc');
    expect(init.body).toBeUndefined();
  });

  it('sends JSON bodies on POST', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(201, { id: 2 }));
    const client = createApiClient(vi.fn().mockResolvedValue('abc'), fetchImpl);

    await client.post('/api/tickets', { title: 'VPN' });

    const [, init] = fetchImpl.mock.calls[0]!;
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"title":"VPN"}');
  });

  it('turns ProblemDetail responses into ApiError', async () => {
    const problem = {
      status: 400,
      title: 'Bad Request',
      detail: 'Request validation failed.',
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'title', message: 'must not be blank' }],
    };
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(400, problem, 'application/problem+json'));
    const client = createApiClient(vi.fn().mockResolvedValue('abc'), fetchImpl);

    const error = await client.post('/api/tickets', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Request validation failed.',
      fieldErrors: [{ field: 'title', message: 'must not be blank' }],
    });
  });

  it('retries a 401 once with a force-refreshed token', async () => {
    const getToken = vi.fn().mockResolvedValueOnce('stale').mockResolvedValueOnce('fresh');
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { status: 401, code: 'UNAUTHENTICATED' }))
      .mockResolvedValueOnce(jsonResponse(200, []));
    const client = createApiClient(getToken, fetchImpl);

    await expect(client.get('/api/tickets')).resolves.toEqual([]);

    expect(getToken).toHaveBeenNthCalledWith(2, true);
    expect(fetchImpl.mock.calls[1]![1].headers.Authorization).toBe('Bearer fresh');
  });

  it('gives up after the retried request is still unauthorized', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(401, { status: 401, code: 'UNAUTHENTICATED' }));
    const client = createApiClient(vi.fn().mockResolvedValue('t'), fetchImpl);

    await expect(client.get('/api/tickets')).rejects.toMatchObject({ status: 401 });
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it('copes with error bodies that are not JSON', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }));
    const client = createApiClient(vi.fn().mockResolvedValue('t'), fetchImpl);

    await expect(client.get('/api/tickets')).rejects.toMatchObject({ status: 502 });
  });
});
