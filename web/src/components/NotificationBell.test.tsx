import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { AppNotification } from '../api/types.ts';
import { aPage, aTicket } from '../test/fixtures.ts';
import { fakeApi, fakeNotificationsApi, renderApp } from '../test/renderApp.tsx';
import { notificationText } from './notificationText.ts';

function aNotification(overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id: 1,
    type: 'STATUS_CHANGED',
    ticketId: 7,
    ticketTitle: 'Yazıcı',
    serviceRequestId: null,
    serviceRequestTitle: null,
    ticketStatus: 'IN_PROGRESS',
    actorId: 'agent-1',
    actorName: 'Agent One',
    createdAt: '2026-10-11T09:00:00Z',
    read: false,
    ...overrides,
  };
}

const tickets = () =>
  fakeApi({
    list: vi.fn().mockResolvedValue(aPage([])),
    get: vi.fn().mockResolvedValue(aTicket({ id: 7, title: 'Yazıcı' })),
  });

describe('notificationText', () => {
  it('builds a sentence per type', () => {
    expect(notificationText(aNotification())).toBe('Agent One durumu "İşlemde" olarak değiştirdi');
    expect(notificationText(aNotification({ type: 'COMMENT_ADDED', ticketStatus: null }))).toBe('Agent One yorum yazdı');
    expect(notificationText(aNotification({ type: 'INTERNAL_COMMENT_ADDED' }))).toBe('Agent One dahili not ekledi');
    expect(notificationText(aNotification({ type: 'AUTO_CLOSED', actorName: 'System' }))).toBe(
      'Yanıt verilmediği için talep otomatik kapatıldı',
    );
    expect(notificationText(aNotification({ type: 'SLA_BREACHED', actorId: null, actorName: null }))).toBe(
      'SLA süresi aşıldı',
    );
    expect(notificationText(aNotification({ type: 'COMMENT_ADDED', actorName: null }))).toBe('Bir kullanıcı yorum yazdı');
    expect(notificationText(aNotification({ type: 'TICKET_RATED', actorName: 'Customer One' }))).toBe(
      'Customer One çözümü puanladı',
    );
    expect(notificationText(aNotification({ type: 'TICKET_ASSIGNED', actorName: 'Manager One' }))).toBe(
      'Manager One talebi size atadı',
    );
    expect(notificationText(aNotification({ type: 'TICKET_UNASSIGNED', actorName: 'Manager One' }))).toBe(
      'Manager One talebi sizden aldı',
    );
    expect(notificationText(aNotification({ type: 'PRIORITY_CHANGED', actorName: 'Manager One' }))).toBe(
      'Manager One önceliği değiştirdi',
    );
  });
});

describe('NotificationBell', () => {
  it('shows the unread count', async () => {
    renderApp('/tickets', {
      api: tickets(),
      notifications: fakeNotificationsApi({ unreadCount: vi.fn().mockResolvedValue({ count: 3 }) }),
    });

    expect(await screen.findByRole('button', { name: 'Bildirimler, 3 okunmamış' })).toHaveTextContent('3');
  });

  it('has no badge without unread notifications', async () => {
    const notifications = fakeNotificationsApi();
    renderApp('/tickets', { api: tickets(), notifications });

    await waitFor(() => expect(notifications.unreadCount).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: 'Bildirimler' })).not.toHaveTextContent(/\d/);
  });

  it('lists notifications and opens the ticket, marking it read', async () => {
    const notifications = fakeNotificationsApi({
      unreadCount: vi.fn().mockResolvedValue({ count: 1 }),
      list: vi.fn().mockResolvedValue(aPage([aNotification(), aNotification({ id: 2, read: true, type: 'COMMENT_ADDED' })])),
    });
    const { router } = renderApp('/tickets', { api: tickets(), notifications });

    await userEvent.click(await screen.findByRole('button', { name: /Bildirimler, 1 okunmamış/ }));
    const panel = await screen.findByRole('dialog', { name: 'Bildirimler' });
    expect(notifications.list).toHaveBeenCalledWith({ size: 10 });
    expect(within(panel).getByText('Agent One durumu "İşlemde" olarak değiştirdi')).toBeInTheDocument();

    await userEvent.click(within(panel).getByText('Agent One durumu "İşlemde" olarak değiştirdi'));

    expect(notifications.markRead).toHaveBeenCalledWith(1);
    expect(router.state.location.pathname).toBe('/tickets/7');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('does not mark an already read notification again', async () => {
    const notifications = fakeNotificationsApi({
      list: vi.fn().mockResolvedValue(aPage([aNotification({ read: true })])),
    });
    renderApp('/tickets', { api: tickets(), notifications });

    await userEvent.click(await screen.findByRole('button', { name: 'Bildirimler' }));
    await userEvent.click(await screen.findByText(/olarak değiştirdi/));

    expect(notifications.markRead).not.toHaveBeenCalled();
  });

  it('marks all as read and refreshes the count', async () => {
    const unreadCount = vi.fn().mockResolvedValueOnce({ count: 2 }).mockResolvedValue({ count: 0 });
    const notifications = fakeNotificationsApi({
      unreadCount,
      list: vi.fn().mockResolvedValue(aPage([aNotification(), aNotification({ id: 2 })])),
    });
    renderApp('/tickets', { api: tickets(), notifications });

    await userEvent.click(await screen.findByRole('button', { name: /2 okunmamış/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Tümünü okundu işaretle' }));

    expect(notifications.markAllRead).toHaveBeenCalled();
    expect(await screen.findByRole('button', { name: 'Bildirimler' })).toBeInTheDocument();
  });

  it('closes with Escape and shows an empty state', async () => {
    renderApp('/tickets', { api: tickets() });

    await userEvent.click(await screen.findByRole('button', { name: 'Bildirimler' }));
    expect(await screen.findByText('Bildirim yok.')).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
