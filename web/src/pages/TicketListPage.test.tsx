import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { aPage, aTicket } from '../test/fixtures.ts';
import { fakeApi, renderApp } from '../test/renderApp.tsx';

describe('TicketListPage', () => {
  it('lists tickets with names, badges and links', async () => {
    const list = vi.fn().mockResolvedValue(
      aPage([
        aTicket({ id: 7, title: 'Yazıcı', requesterId: 'cust-1', assigneeId: 'agent-1', assigneeName: 'Agent One' }),
      ]),
    );
    renderApp('/tickets', { role: 'CUSTOMER', api: fakeApi({ list }) });

    const row = (await screen.findByRole('link', { name: 'Yazıcı' })).closest('tr')!;
    expect(screen.getByRole('link', { name: 'Yazıcı' })).toHaveAttribute('href', '/tickets/7');
    expect(within(row).getByText('Siz')).toBeInTheDocument(); // own ticket
    expect(within(row).getByText('Agent One')).toBeInTheDocument();
    expect(within(row).getByText('Yeni')).toBeInTheDocument();
    expect(within(row).getByText('Yüksek')).toBeInTheDocument();
    expect(within(row).getByText('7 sa kaldı')).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith({ status: undefined, page: 0, size: 20 });
  });

  it('filters by status through the URL', async () => {
    const list = vi.fn().mockResolvedValue(aPage([]));
    const { router } = renderApp('/tickets', { role: 'AGENT', api: fakeApi({ list }) });
    await screen.findByText('Henüz talep yok.');

    await userEvent.selectOptions(screen.getByLabelText('Durum'), 'RESOLVED');

    expect(router.state.location.search).toBe('?status=RESOLVED');
    expect(await screen.findByText('Bu durumda talep yok.')).toBeInTheDocument();
    expect(list).toHaveBeenLastCalledWith({ status: 'RESOLVED', page: 0, size: 20 });
  });

  it('pages through results', async () => {
    const list = vi
      .fn()
      .mockImplementation(({ page }: { page: number }) =>
        Promise.resolve(aPage([aTicket({ id: page + 1, title: `Talep ${page + 1}` })], { page, totalPages: 2, totalElements: 2 })),
      );
    renderApp('/tickets', { api: fakeApi({ list }) });

    await screen.findByText('Talep 1');
    expect(screen.getByRole('button', { name: 'Önceki' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Sonraki' }));

    expect(await screen.findByText('Talep 2')).toBeInTheDocument();
    expect(screen.getByText(/Sayfa 2 \/ 2/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sonraki' })).toBeDisabled();
  });
});
