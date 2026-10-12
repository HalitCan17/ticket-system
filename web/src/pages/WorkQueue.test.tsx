import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { aHistoryEntry, aPage, aTicket } from '../test/fixtures.ts';
import { fakeApi, renderApp } from '../test/renderApp.tsx';

describe('work queue on the ticket list', () => {
  it('lets staff pick their queue and SLA order through the URL', async () => {
    const list = vi.fn().mockResolvedValue(aPage([]));
    const { router } = renderApp('/tickets', { role: 'AGENT', api: fakeApi({ list }) });
    await screen.findByText('Burada talep yok.');

    await userEvent.click(screen.getByRole('button', { name: 'Bana atananlar' }));
    expect(router.state.location.search).toBe('?assignee=me');
    expect(screen.getByRole('button', { name: 'Bana atananlar' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.selectOptions(screen.getByLabelText('Sıralama'), 'sla');
    expect(router.state.location.search).toBe('?assignee=me&sort=sla');
    await screen.findByText('Aramaya uyan talep yok.');
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ assignee: 'me', sort: 'SLA' }));

    await userEvent.click(screen.getByRole('button', { name: 'Atanmamışlar' }));
    expect(router.state.location.search).toBe('?assignee=unassigned&sort=sla');
  });

  it('is not offered to customers, and their URL parameters are ignored', async () => {
    const list = vi.fn().mockResolvedValue(aPage([]));
    renderApp('/tickets?assignee=me&sort=sla', { role: 'CUSTOMER', api: fakeApi({ list }) });
    await screen.findByText('Burada talep yok.');

    expect(screen.queryByRole('group', { name: 'İş kuyruğu' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Sıralama')).not.toBeInTheDocument();
    expect(list).toHaveBeenCalledWith(expect.objectContaining({ assignee: undefined, sort: undefined }));
  });
});

describe('assignment and editing on the ticket page', () => {
  it('an agent claims an unassigned ticket', async () => {
    const assign = vi.fn().mockResolvedValue(aTicket({ id: 5, assigneeId: 'agent-1', assigneeName: 'Agent One' }));
    const api = fakeApi({ get: vi.fn().mockResolvedValue(aTicket({ id: 5, canClaim: true })), assign });
    renderApp('/tickets/5', { role: 'AGENT', api });

    await userEvent.click(await screen.findByRole('button', { name: 'Üstlen' }));

    expect(assign).toHaveBeenCalledWith(5, 'agent-1'); // the signed-in agent
    expect(await screen.findByText('Siz')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Üstlen' })).not.toBeInTheDocument();
  });

  it('an agent hands over their own ticket to a colleague or releases it', async () => {
    const own = aTicket({ id: 5, assigneeId: 'agent-1', canReassign: true });
    const assign = vi.fn().mockResolvedValue(own); // the panel stays, so both actions can be tried
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(own),
      staff: vi.fn().mockResolvedValue([
        { id: 'agent-1', name: 'Agent One', role: 'AGENT' },
        { id: 'agent-2', name: 'Agent Two', role: 'AGENT' },
        { id: 'manager-1', name: 'Manager One', role: 'MANAGER' },
      ]),
      assign,
    });
    renderApp('/tickets/5', { role: 'AGENT', api });

    const panel = await screen.findByRole('region', { name: 'Atama' });
    const select = within(panel).getByLabelText('Devredilecek kişi');
    // The current assignee is not offered again.
    await within(panel).findByRole('option', { name: 'Agent Two' });
    expect(within(select).queryByRole('option', { name: /Agent One/ })).not.toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Devret' })).toBeDisabled();

    await userEvent.selectOptions(select, 'manager-1');
    await userEvent.click(within(panel).getByRole('button', { name: 'Devret' }));
    expect(assign).toHaveBeenLastCalledWith(5, 'manager-1');

    await userEvent.click(within(panel).getByRole('button', { name: 'Bırak' }));
    expect(assign).toHaveBeenLastCalledWith(5, null);
  });

  it('hides assignment controls the backend does not allow', async () => {
    const api = fakeApi({ get: vi.fn().mockResolvedValue(aTicket({ id: 5, assigneeId: 'agent-9' })) });
    renderApp('/tickets/5', { role: 'AGENT', api });

    await screen.findByRole('heading', { name: /VPN/ });
    expect(screen.queryByRole('region', { name: 'Atama' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Önceliği/ })).not.toBeInTheDocument();
  });

  it('a priority change needs a reason; only changed fields are sent', async () => {
    const update = vi.fn().mockResolvedValue(aTicket({ id: 5, priority: 'CRITICAL', canEdit: true }));
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(aTicket({ id: 5, priority: 'LOW', category: 'OTHER', canEdit: true })),
      update,
    });
    renderApp('/tickets/5', { role: 'MANAGER', api });

    await userEvent.click(await screen.findByRole('button', { name: 'Önceliği / kategoriyi değiştir' }));
    const form = screen.getByRole('form', { name: 'Talebi düzenle' });
    const save = within(form).getByRole('button', { name: 'Kaydet' });
    expect(save).toBeDisabled(); // nothing changed

    await userEvent.selectOptions(within(form).getByLabelText('Öncelik'), 'CRITICAL');
    expect(save).toBeDisabled(); // reason missing
    await userEvent.type(within(form).getByLabelText('Öncelik değişikliğinin gerekçesi'), 'Üretim durdu');
    await userEvent.click(save);

    expect(update).toHaveBeenCalledWith(5, { priority: 'CRITICAL', category: undefined, reason: 'Üretim durdu' });
  });

  it('merges staff activities into the history in time order', async () => {
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(aTicket({ id: 5, status: 'IN_PROGRESS' })),
      history: vi.fn().mockResolvedValue([
        aHistoryEntry({ changedAt: '2026-10-10T09:00:00Z' }),
        aHistoryEntry({ toStatus: 'IN_PROGRESS', changedAt: '2026-10-10T11:00:00Z' }),
      ]),
      activities: vi.fn().mockResolvedValue([
        {
          id: 1,
          type: 'PRIORITY_CHANGED',
          oldValue: 'HIGH',
          oldName: null,
          newValue: 'CRITICAL',
          newName: null,
          note: 'Üretim durdu',
          actorId: 'manager-1',
          actorName: 'Manager One',
          createdAt: '2026-10-10T10:00:00Z',
        },
      ]),
    });
    renderApp('/tickets/5', { role: 'MANAGER', api });

    const side = await screen.findByRole('complementary');
    await within(side).findByText('Öncelik: Yüksek → Kritik');
    const items = within(side).getAllByRole('listitem').map((li) => li.querySelector('strong')?.textContent);
    expect(items).toEqual(['Yeni', 'Öncelik: Yüksek → Kritik', 'İşlemde']);
    expect(within(side).getByText('Üretim durdu')).toBeInTheDocument();
  });

  it('never asks for activities as a customer', async () => {
    const activities = vi.fn().mockResolvedValue([]);
    const api = fakeApi({ get: vi.fn().mockResolvedValue(aTicket({ id: 5 })), activities });
    renderApp('/tickets/5', { role: 'CUSTOMER', api });

    await screen.findByRole('heading', { name: /VPN/ });
    expect(activities).not.toHaveBeenCalled();
  });
});
