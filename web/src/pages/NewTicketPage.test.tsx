import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client.ts';
import { aTicket } from '../test/fixtures.ts';
import { fakeApi, renderApp } from '../test/renderApp.tsx';

describe('NewTicketPage', () => {
  it('creates a ticket and opens it', async () => {
    const create = vi.fn().mockResolvedValue(aTicket({ id: 42 }));
    const get = vi.fn().mockResolvedValue(aTicket({ id: 42, title: 'E-posta açılmıyor' }));
    const { router } = renderApp('/tickets/new', { api: fakeApi({ create, get }) });

    await userEvent.type(screen.getByLabelText('Başlık'), '  E-posta açılmıyor ');
    await userEvent.type(screen.getByLabelText('Açıklama'), 'Outlook hata veriyor');
    expect(screen.getByLabelText('Kategori')).toHaveValue('OTHER');
    await userEvent.selectOptions(screen.getByLabelText('Kategori'), 'NETWORK');
    await userEvent.selectOptions(screen.getByLabelText('Öncelik'), 'CRITICAL');
    await userEvent.click(screen.getByRole('button', { name: 'Talep aç' }));

    expect(create).toHaveBeenCalledWith({
      title: 'E-posta açılmıyor',
      description: 'Outlook hata veriyor',
      priority: 'CRITICAL',
      category: 'NETWORK',
    });
    expect(await screen.findByRole('heading', { name: /E-posta açılmıyor/ })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/tickets/42');
  });

  it('shows backend validation errors under the field', async () => {
    const create = vi.fn().mockRejectedValue(
      new ApiError({
        status: 400,
        code: 'VALIDATION_FAILED',
        detail: 'Request validation failed.',
        errors: [{ field: 'title', message: 'size must be between 0 and 200' }],
      }),
    );
    renderApp('/tickets/new', { api: fakeApi({ create }) });

    await userEvent.type(screen.getByLabelText('Başlık'), 'x');
    await userEvent.type(screen.getByLabelText('Açıklama'), 'y');
    await userEvent.click(screen.getByRole('button', { name: 'Talep aç' }));

    expect(await screen.findByText('size must be between 0 and 200')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows other errors as a message', async () => {
    const create = vi.fn().mockRejectedValue(new ApiError({ status: 500, detail: 'An unexpected error occurred.' }));
    renderApp('/tickets/new', { api: fakeApi({ create }) });

    await userEvent.type(screen.getByLabelText('Başlık'), 'x');
    await userEvent.type(screen.getByLabelText('Açıklama'), 'y');
    await userEvent.click(screen.getByRole('button', { name: 'Talep aç' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred.');
  });
});
