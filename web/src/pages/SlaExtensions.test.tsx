import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { AppNotification } from '../api/types.ts';
import { firstResponseText } from '../components/Badges.tsx';
import { notificationText } from '../components/notificationText.ts';
import { aPage, aTicket } from '../test/fixtures.ts';
import { fakeApi, renderApp } from '../test/renderApp.tsx';

const CREATED = '2026-10-10T09:00:00Z';

describe('first response text', () => {
  it('describes every state', () => {
    const base = { dueAt: '2026-10-10T10:00:00Z', breached: false };
    expect(firstResponseText(null, CREATED)).toBe('—');
    expect(firstResponseText({ ...base, respondedAt: null, remainingSeconds: 1800 }, CREATED)).toBe('30 dk kaldı');
    expect(firstResponseText({ ...base, respondedAt: null, remainingSeconds: -600, breached: true }, CREATED)).toBe(
      '10 dk gecikti',
    );
    expect(firstResponseText({ ...base, respondedAt: '2026-10-10T09:12:00Z', remainingSeconds: null }, CREATED)).toBe(
      '12 dk içinde yanıtlandı',
    );
    expect(
      firstResponseText({ ...base, respondedAt: '2026-10-10T11:00:00Z', remainingSeconds: null, breached: true }, CREATED),
    ).toBe('2 sa içinde yanıtlandı (geç)');
    expect(firstResponseText({ ...base, respondedAt: null, remainingSeconds: null }, CREATED)).toBe(
      'Yanıt verilmeden kapandı',
    );
  });

  it('notification sentences for the new SLA events', () => {
    const n = (type: AppNotification['type']): AppNotification => ({
      id: 1, type, ticketId: 7, ticketTitle: 'VPN', ticketStatus: null, actorId: null, actorName: null,
      createdAt: CREATED, read: false,
    });
    expect(notificationText(n('SLA_WARNING'))).toBe("SLA süresinin %80'i doldu");
    expect(notificationText(n('FIRST_RESPONSE_BREACHED'))).toBe('İlk yanıt süresi aşıldı');
  });
});

describe('escalation and first response on screens', () => {
  it('the ticket page shows the first response and the escalated badge', async () => {
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(
        aTicket({
          id: 7,
          createdAt: CREATED,
          escalatedAt: '2026-10-10T17:00:00Z',
          firstResponse: { dueAt: '2026-10-10T10:00:00Z', respondedAt: null, breached: true, remainingSeconds: -3600 },
        }),
      ),
    });
    renderApp('/tickets/7', { role: 'AGENT', api });

    expect(await screen.findByText('Eskale edildi')).toBeInTheDocument();
    const side = screen.getByRole('complementary');
    expect(within(side).getByText('1 sa gecikti')).toBeInTheDocument();
  });

  it('staff pick the escalated queue', async () => {
    const list = vi.fn().mockResolvedValue(aPage([aTicket({ id: 3, escalatedAt: '2026-10-10T17:00:00Z' })]));
    const { router } = renderApp('/tickets', { role: 'MANAGER', api: fakeApi({ list }) });
    const row = (await screen.findByRole('link', { name: 'VPN bağlanmıyor' })).closest('tr')!;
    expect(within(row).getByText('Eskale edildi')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Eskale edilenler' }));
    expect(router.state.location.search).toBe('?escalated=1');
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ escalated: true }));
    expect(screen.getByRole('button', { name: 'Eskale edilenler' })).toHaveAttribute('aria-pressed', 'true');
  });
});
