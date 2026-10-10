import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client.ts';
import { aComment, aHistoryEntry, anSla, aTicket } from '../test/fixtures.ts';
import { fakeApi, renderApp } from '../test/renderApp.tsx';

describe('TicketDetailPage', () => {
  it('shows ticket, history and comments', async () => {
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(
        aTicket({ id: 5, title: 'VPN', assigneeId: 'agent-1', assigneeName: 'Agent One', status: 'IN_PROGRESS' }),
      ),
      history: vi.fn().mockResolvedValue([
        aHistoryEntry(),
        aHistoryEntry({ fromStatus: 'NEW', toStatus: 'IN_PROGRESS', changedBy: 'agent-1', changedByName: 'Agent One' }),
      ]),
      comments: vi.fn().mockResolvedValue([
        aComment({ id: 1, visibility: 'INTERNAL', body: 'Lisans sorunu' }),
        aComment({ id: 2, body: 'Hangi sürüm?' }),
      ]),
    });
    renderApp('/tickets/5', { role: 'AGENT', api });

    expect(await screen.findByRole('heading', { name: /VPN/ })).toBeInTheDocument();
    const side = screen.getByRole('complementary');
    expect(within(side).getByText('Customer One')).toBeInTheDocument();
    expect(await within(side).findByText('İşlemde')).toBeInTheDocument(); // history loads after the ticket

    const internal = (await screen.findByText('Lisans sorunu')).closest('li')!;
    expect(within(internal).getByText('Dahili')).toBeInTheDocument();
    expect(within(screen.getByText('Hangi sürüm?').closest('li')!).queryByText('Dahili')).not.toBeInTheDocument();
  });

  it('applies a transition and refreshes the ticket', async () => {
    const transition = vi
      .fn()
      .mockResolvedValue(aTicket({ id: 5, status: 'IN_PROGRESS', allowedTransitions: ['WAITING_FOR_CUSTOMER', 'RESOLVED'] }));
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(aTicket({ id: 5, allowedTransitions: ['IN_PROGRESS'] })),
      transition,
    });
    renderApp('/tickets/5', { role: 'AGENT', api });

    await userEvent.click(await screen.findByRole('button', { name: 'İşleme al' }));

    expect(transition).toHaveBeenCalledWith(5, { targetStatus: 'IN_PROGRESS', note: undefined });
    expect(await screen.findByRole('button', { name: 'Çözüldü olarak işaretle' })).toBeInTheDocument();
  });

  it('shows the backend error when a transition is rejected', async () => {
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(aTicket({ id: 5, status: 'RESOLVED', allowedTransitions: ['CLOSED'] })),
      transition: vi.fn().mockRejectedValue(
        new ApiError({ status: 409, code: 'INVALID_STATUS_TRANSITION', detail: 'Ticket cannot move from CLOSED to CLOSED.' }),
      ),
    });
    renderApp('/tickets/5', { role: 'CUSTOMER', api });

    await userEvent.click(await screen.findByRole('button', { name: 'Onayla ve kapat' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ticket cannot move from CLOSED to CLOSED.');
  });

  it('tells the customer when a resolved ticket closes automatically', async () => {
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(
        aTicket({
          status: 'RESOLVED',
          resolutionNote: 'Profil yeniden oluşturuldu',
          autoCloseAt: '2026-10-17T09:00:00Z',
          sla: anSla({ state: 'STOPPED', dueAt: null }),
        }),
      ),
    });
    renderApp('/tickets/1', { role: 'CUSTOMER', api });

    expect(await screen.findByText('Profil yeniden oluşturuldu')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/otomatik kapanacak/);
    expect(screen.getByText('SLA karşılandı')).toBeInTheDocument();
  });

  it('adds a comment', async () => {
    const addComment = vi.fn().mockResolvedValue(aComment({ id: 9, authorId: 'cust-1', body: 'Teşekkürler' }));
    const comments = vi.fn().mockResolvedValue([]);
    const api = fakeApi({ get: vi.fn().mockResolvedValue(aTicket()), addComment, comments });
    renderApp('/tickets/1', { role: 'CUSTOMER', api });

    await userEvent.type(await screen.findByLabelText('Yorum'), 'Teşekkürler');
    await userEvent.click(screen.getByRole('button', { name: 'Yorum ekle' }));

    expect(addComment).toHaveBeenCalledWith(1, { body: 'Teşekkürler', visibility: 'EXTERNAL' });
    expect(comments).toHaveBeenCalledTimes(2); // refreshed after adding
  });

  it('hides the comment form on closed tickets', async () => {
    renderApp('/tickets/1', { api: fakeApi({ get: vi.fn().mockResolvedValue(aTicket({ status: 'CLOSED' })) }) });

    expect(await screen.findByText('Kapalı taleplere yorum eklenemez.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Yorum')).not.toBeInTheDocument();
  });

  it('shows not found for a missing or foreign ticket', async () => {
    const api = fakeApi({
      get: vi.fn().mockRejectedValue(new ApiError({ status: 404, code: 'TICKET_NOT_FOUND', detail: 'Ticket 99 not found.' })),
    });
    renderApp('/tickets/99', { api });

    expect(await screen.findByRole('heading', { name: 'Talep bulunamadı' })).toBeInTheDocument();
  });
});
