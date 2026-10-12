import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { ReportSummary, TeamMember } from '../api/types.ts';
import { aPage } from '../test/fixtures.ts';
import { fakeApi, fakeReportsApi, renderApp } from '../test/renderApp.tsx';

function aSummary(overrides: Partial<ReportSummary> = {}): ReportSummary {
  return {
    days: 30,
    from: '2026-09-12T10:00:00Z',
    to: '2026-10-12T10:00:00Z',
    statusCounts: { NEW: 4, IN_PROGRESS: 3, WAITING_FOR_CUSTOMER: 1, RESOLVED: 2, CLOSED: 10 },
    openTickets: 8,
    unassignedOpen: 2,
    breachedOpen: 1,
    created: 12,
    resolution: { resolved: 8, withinSla: 6, withinSlaRate: 0.75, avgResolveSeconds: 5400 },
    satisfaction: { count: 4, average: 4.25, distribution: [0, 0, 1, 1, 2] },
    categories: { HARDWARE: 3, SOFTWARE: 5, NETWORK: 2, ACCESS: 1, OTHER: 1 },
    ...overrides,
  };
}

function aMember(overrides: Partial<TeamMember> = {}): TeamMember {
  return {
    id: 'agent-1',
    name: 'Agent One',
    role: 'AGENT',
    openWork: 5,
    breachedOpen: 1,
    resolved: 7,
    ratings: 3,
    averageRating: 4.5,
    ...overrides,
  };
}

describe('DashboardPage', () => {
  it('shows current and period figures, distributions and the team', async () => {
    const reports = fakeReportsApi({
      summary: vi.fn().mockResolvedValue(aSummary()),
      team: vi.fn().mockResolvedValue([aMember(), aMember({ id: 'manager-1', name: 'Manager One', role: 'MANAGER', openWork: 0, breachedOpen: 0, ratings: 0, averageRating: null })]),
    });
    renderApp('/dashboard', { role: 'MANAGER', reports });

    expect(await screen.findByRole('link', { name: 'Açık talep: 8' })).toHaveAttribute('href', '/tickets');
    expect(screen.getByRole('link', { name: 'Atanmamış: 2' })).toHaveAttribute(
      'href',
      '/tickets?assignee=unassigned&sort=sla',
    );
    expect(screen.getByRole('link', { name: "SLA'sı aşılmış: 1" })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'SLA uyumu: %75' })).toHaveTextContent('6 / 8 talep hedefinde çözüldü');
    expect(screen.getByRole('group', { name: 'Ortalama çözüm süresi: 1 sa 30 dk' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Memnuniyet: 4,3 / 5' })).toHaveTextContent('4 puan');

    const statuses = screen.getByRole('list', { name: 'Statü dağılımı' });
    expect(within(statuses).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Yeni4',
      'İşlemde3',
      'Müşteri bekleniyor1',
      'Çözüldü2',
      'Kapalı10',
    ]);
    expect(within(statuses).getByText('Kapalı').closest('li')).toHaveAttribute('title', 'Kapalı: 10 talep (%50)');
    const ratings = screen.getByRole('list', { name: 'Memnuniyet dağılımı' });
    expect(within(ratings).getAllByRole('listitem')[0]).toHaveTextContent('5 – Çok iyi2');

    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(within(rows[0]!).getByRole('link', { name: 'Agent One' })).toHaveAttribute(
      'href',
      '/tickets?assignee=agent-1&sort=sla',
    );
    expect(rows[0]).toHaveTextContent('4,5 (3)');
    expect(rows[1]).toHaveTextContent('Manager One · yönetici');
    expect(rows[1]).toHaveTextContent('—');
  });

  it('explains empty periods instead of showing zero rates', async () => {
    const reports = fakeReportsApi({
      summary: vi.fn().mockResolvedValue(
        aSummary({
          resolution: { resolved: 0, withinSla: 0, withinSlaRate: null, avgResolveSeconds: null },
          satisfaction: { count: 0, average: null, distribution: [0, 0, 0, 0, 0] },
        }),
      ),
    });
    renderApp('/dashboard', { role: 'MANAGER', reports });

    expect(await screen.findByRole('group', { name: 'SLA uyumu: —' })).toHaveTextContent('Bu dönemde çözülen talep yok');
    expect(screen.getByRole('group', { name: 'Memnuniyet: —' })).toHaveTextContent('Bu dönemde puan verilmedi');
    expect(await screen.findByText('Henüz giriş yapmış personel yok.')).toBeInTheDocument();
  });

  it('switches the period through the URL', async () => {
    const summary = vi.fn().mockImplementation((days: number) => Promise.resolve(aSummary({ days })));
    const reports = fakeReportsApi({ summary });
    const { router } = renderApp('/dashboard', { role: 'MANAGER', reports });
    await screen.findByText('Son 30 gün', { selector: 'h2' });

    await userEvent.click(screen.getByRole('button', { name: 'Son 7 gün' }));

    expect(router.state.location.search).toBe('?days=7');
    expect(await screen.findByText('Son 7 gün', { selector: 'h2' })).toBeInTheDocument();
    expect(summary).toHaveBeenLastCalledWith(7);
    expect(screen.getByRole('button', { name: 'Son 7 gün' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('is for managers only', async () => {
    const reports = fakeReportsApi();
    renderApp('/dashboard', { role: 'AGENT', reports });

    expect(await screen.findByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Panel' })).not.toBeInTheDocument();
    expect(reports.summary).not.toHaveBeenCalled();
  });

  it('the team link opens that person’s queue on the ticket list', async () => {
    const list = vi.fn().mockResolvedValue(aPage([]));
    renderApp('/tickets?assignee=agent-7&sort=sla', { role: 'MANAGER', api: fakeApi({ list }) });

    expect(await screen.findByText(/Kuyruk: seçili kişi/)).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith(expect.objectContaining({ assignee: 'agent-7', sort: 'SLA' }));
    await userEvent.click(screen.getByRole('button', { name: 'Kişi filtresini kaldır' }));
    expect(screen.queryByText(/Kuyruk:/)).not.toBeInTheDocument();
  });
});
