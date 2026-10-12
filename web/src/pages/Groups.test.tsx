import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { SupportGroup } from '../api/types.ts';
import { aHistoryEntry, aPage, aTicket } from '../test/fixtures.ts';
import { fakeApi, fakeGroupsApi, fakeReportsApi, renderApp } from '../test/renderApp.tsx';

function aGroup(overrides: Partial<SupportGroup> = {}): SupportGroup {
  return {
    id: 3,
    name: 'Ağ ekibi',
    description: 'İnternet ve VPN',
    assignmentMode: 'NONE',
    categories: ['NETWORK'],
    members: [],
    ...overrides,
  };
}

describe('GroupsPage', () => {
  it('shows groups with their routing, mode and members; for managers only', async () => {
    const groups = fakeGroupsApi({
      list: vi.fn().mockResolvedValue([
        aGroup({ assignmentMode: 'ROUND_ROBIN', members: [{ id: 'agent-1', name: 'Agent One', role: 'AGENT' }] }),
        aGroup({ id: 4, name: 'Genel destek', description: null, categories: ['OTHER', 'ACCESS'] }),
      ]),
    });
    renderApp('/groups', { role: 'MANAGER', groups });

    const card = await screen.findByRole('listitem', { name: 'Ağ ekibi' });
    expect(card).toHaveTextContent('Üyelere sırayla ata');
    expect(card).toHaveTextContent('Agent One');
    expect(screen.getByRole('listitem', { name: 'Genel destek' })).toHaveTextContent('Diğer, Hesap / erişim');
    expect(screen.getByRole('listitem', { name: 'Genel destek' })).toHaveTextContent('Üye yok');
    expect(screen.getByLabelText('Ağ grubu')).toHaveValue('3');
    expect(screen.getByLabelText('Donanım grubu')).toHaveValue('');
    expect(screen.getByRole('link', { name: 'Gruplar' })).toHaveAttribute('href', '/groups');
  });

  it('tells other roles the page is for managers', async () => {
    const groups = fakeGroupsApi();
    renderApp('/groups', { role: 'AGENT', groups });
    expect(await screen.findByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeInTheDocument();
    expect(groups.list).not.toHaveBeenCalled();
  });

  it('creates a group, saves routing and members', async () => {
    const groups = fakeGroupsApi({
      list: vi.fn().mockResolvedValue([aGroup()]),
      create: vi.fn().mockResolvedValue(aGroup({ id: 9, name: 'Sunucu ekibi' })),
      setRoutes: vi.fn().mockResolvedValue({}),
      setMembers: vi.fn().mockResolvedValue({ group: aGroup(), releasedTickets: 0 }),
    });
    const api = fakeApi({
      staff: vi.fn().mockResolvedValue([
        { id: 'agent-1', name: 'Agent One', role: 'AGENT' },
        { id: 'manager-1', name: 'Manager One', role: 'MANAGER' },
      ]),
    });
    renderApp('/groups', { role: 'MANAGER', groups, api });
    await screen.findByRole('listitem', { name: 'Ağ ekibi' });

    await userEvent.click(screen.getByRole('button', { name: '+ Yeni grup' }));
    const form = screen.getByRole('form', { name: 'Yeni grup' });
    await userEvent.type(within(form).getByLabelText('Ad'), ' Sunucu ekibi ');
    await userEvent.click(within(form).getByRole('radio', { name: 'En az açık işi olana ata' }));
    await userEvent.click(within(form).getByRole('button', { name: 'Kaydet' }));
    expect(groups.create).toHaveBeenCalledWith({ name: 'Sunucu ekibi', description: undefined, assignmentMode: 'LEAST_LOADED' });

    const routing = screen.getByRole('form', { name: 'Yönlendirme' });
    const save = within(routing).getByRole('button', { name: 'Yönlendirmeyi kaydet' });
    expect(save).toBeDisabled(); // nothing changed yet
    await userEvent.selectOptions(within(routing).getByLabelText('Donanım grubu'), '3');
    await userEvent.click(save);
    expect(groups.setRoutes).toHaveBeenCalledWith({ HARDWARE: 3, SOFTWARE: null, NETWORK: 3, ACCESS: null, OTHER: null });

    const card = screen.getByRole('listitem', { name: 'Ağ ekibi' });
    await userEvent.click(within(card).getByRole('button', { name: 'Üyeler' }));
    const members = await within(card).findByRole('form', { name: 'Ağ ekibi üyeleri' });
    await userEvent.click(await within(members).findByRole('checkbox', { name: 'Agent One' }));
    await userEvent.click(within(members).getByRole('button', { name: 'Üyeleri kaydet' }));
    expect(groups.setMembers).toHaveBeenCalledWith(3, ['agent-1']);
  });
});

describe('removing group members', () => {
  it('warns before saving and reports how many open tickets moved', async () => {
    const groups = fakeGroupsApi({
      list: vi.fn().mockResolvedValue([
        aGroup({ assignmentMode: 'ROUND_ROBIN', members: [{ id: 'agent-1', name: 'Agent One', role: 'AGENT' }] }),
      ]),
      setMembers: vi.fn().mockResolvedValue({ group: aGroup(), releasedTickets: 2 }),
    });
    const api = fakeApi({ staff: vi.fn().mockResolvedValue([{ id: 'agent-1', name: 'Agent One', role: 'AGENT' }]) });
    renderApp('/groups', { role: 'MANAGER', groups, api });

    const card = await screen.findByRole('listitem', { name: 'Ağ ekibi' });
    await userEvent.click(within(card).getByRole('button', { name: 'Üyeler' }));
    await userEvent.click(await within(card).findByRole('checkbox', { name: 'Agent One' }));
    expect(card).toHaveTextContent('Agent One gruptan çıkarılacak. Bu gruptaki açık talepleri onlardan alınıp kalan üyelere dağıtılacak.');

    await userEvent.click(within(card).getByRole('button', { name: 'Üyeleri kaydet' }));
    expect(groups.setMembers).toHaveBeenCalledWith(3, []);
    expect(await within(card).findByRole('status')).toHaveTextContent('2 açık talebi');
  });
});

describe('groups on tickets', () => {
  it('staff can pick "my groups" as their queue and see the group column', async () => {
    const list = vi.fn().mockResolvedValue(aPage([aTicket({ id: 2, groupId: 3, groupName: 'Ağ ekibi' })]));
    const { router } = renderApp('/tickets', { role: 'AGENT', api: fakeApi({ list }) });
    const row = (await screen.findByRole('link', { name: 'VPN bağlanmıyor' })).closest('tr')!;
    expect(within(row).getByText('Ağ ekibi')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Grubumun talepleri' }));
    expect(router.state.location.search).toBe('?group=mine');
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ group: 'mine', assignee: undefined }));
    // Choosing a person queue drops the group filter.
    await userEvent.click(screen.getByRole('button', { name: 'Bana atananlar' }));
    expect(router.state.location.search).toBe('?assignee=me');
  });

  it('customers see the group name but no group column', async () => {
    const list = vi.fn().mockResolvedValue(aPage([aTicket({ id: 2, groupId: 3, groupName: 'Ağ ekibi' })]));
    renderApp('/tickets', { role: 'CUSTOMER', api: fakeApi({ list }) });
    await screen.findByRole('link', { name: 'VPN bağlanmıyor' });
    expect(screen.queryByRole('columnheader', { name: 'Grup' })).not.toBeInTheDocument();
  });

  it('staff move a ticket to another group from the edit panel; group changes show in the history', async () => {
    const update = vi.fn().mockResolvedValue(aTicket({ id: 5, groupId: 4, groupName: 'Genel destek', canEdit: true }));
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(aTicket({ id: 5, groupId: 3, groupName: 'Ağ ekibi', canEdit: true })),
      history: vi.fn().mockResolvedValue([aHistoryEntry()]),
      activities: vi.fn().mockResolvedValue([
        {
          id: 1, type: 'GROUP_CHANGED', oldValue: '3', oldName: 'Ağ ekibi', newValue: '8', newName: null,
          note: null, actorId: 'manager-1', actorName: 'Manager One', createdAt: '2026-10-10T10:00:00Z',
        },
      ]),
      update,
    });
    const groups = fakeGroupsApi({ list: vi.fn().mockResolvedValue([aGroup(), aGroup({ id: 4, name: 'Genel destek' })]) });
    renderApp('/tickets/5', { role: 'MANAGER', api, groups });

    const side = await screen.findByRole('complementary');
    expect(within(side).getByText('Ağ ekibi')).toBeInTheDocument();
    expect(await within(side).findByText('Grup: Ağ ekibi → silinmiş grup')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Önceliği / kategoriyi / grubu değiştir' }));
    const form = screen.getByRole('form', { name: 'Talebi düzenle' });
    await within(form).findByRole('option', { name: 'Genel destek' });
    await userEvent.selectOptions(within(form).getByLabelText('Destek grubu'), '4');
    await userEvent.click(within(form).getByRole('button', { name: 'Kaydet' }));
    expect(update).toHaveBeenCalledWith(5, expect.objectContaining({ groupId: 4, removeGroup: undefined }));
  });

  it('the dashboard lists group workload and flags groups without members', async () => {
    const reports = fakeReportsApi({
      summary: vi.fn().mockResolvedValue({
        days: 30, from: '', to: '', statusCounts: {}, openTickets: 0, unassignedOpen: 0, breachedOpen: 0, created: 0,
        resolution: { resolved: 0, withinSla: 0, withinSlaRate: null, avgResolveSeconds: null },
        satisfaction: { count: 0, average: null, distribution: [0, 0, 0, 0, 0] }, categories: {},
      }),
      groups: vi.fn().mockResolvedValue([
        { id: 3, name: 'Ağ ekibi', members: 0, openWork: 4, unassignedOpen: 4, breachedOpen: 1 },
        { id: null, name: null, members: 0, openWork: 2, unassignedOpen: 1, breachedOpen: 0 },
      ]),
    });
    renderApp('/dashboard', { role: 'MANAGER', reports });

    const table = await screen.findByRole('table', { name: 'Grup iş yükü' });
    expect(within(table).getByRole('link', { name: 'Ağ ekibi' })).toHaveAttribute('href', '/tickets?group=3&sort=sla');
    expect(within(table).getByText('Grupsuz talepler')).toBeInTheDocument();
  });
});
