import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { SlaCalendar } from '../api/types.ts';
import { fakeSlaCalendarApi, renderApp } from '../test/renderApp.tsx';
import { targetText } from './SlaSettingsPage.tsx';

function aCalendar(overrides: Partial<SlaCalendar> = {}): SlaCalendar {
  return {
    enabled: false,
    zoneId: 'Europe/Istanbul',
    workDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    start: '09:00',
    end: '18:00',
    holidays: [
      { date: '2026-10-29', name: 'Cumhuriyet Bayramı' },
      { date: '2027-01-01', name: 'Yılbaşı' },
    ],
    resolutionSeconds: { CRITICAL: 4 * 3600, HIGH: 8 * 3600, MEDIUM: 24 * 3600, LOW: 72 * 3600 },
    firstResponseSeconds: { CRITICAL: 1800, HIGH: 3600, MEDIUM: 4 * 3600, LOW: 8 * 3600 },
    raisePriorityOnBreach: false,
    ...overrides,
  };
}

describe('targetText', () => {
  it('writes targets in hours, never days (a working day is not 24 h)', () => {
    expect(targetText(1800)).toBe('30 dk');
    expect(targetText(3600)).toBe('1 saat');
    expect(targetText(5400)).toBe('1 saat 30 dk');
    expect(targetText(72 * 3600)).toBe('72 saat');
  });
});

describe('SlaSettingsPage', () => {
  it('shows the targets and tells other roles the page is for managers', async () => {
    const slaCalendar = fakeSlaCalendarApi({ get: vi.fn().mockResolvedValue(aCalendar()) });
    renderApp('/sla', { role: 'MANAGER', slaCalendar });

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row').slice(1).map((r) => r.textContent);
    expect(rows).toEqual(['Kritik30 dk4 saat', 'Yüksek1 saat8 saat', 'Orta4 saat24 saat', 'Düşük8 saat72 saat']);
    expect(screen.getByRole('link', { name: 'SLA' })).toHaveAttribute('href', '/sla');
  });

  it('is not available to agents', async () => {
    const slaCalendar = fakeSlaCalendarApi();
    renderApp('/sla', { role: 'AGENT', slaCalendar });

    expect(await screen.findByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeInTheDocument();
    expect(slaCalendar.get).not.toHaveBeenCalled();
    expect(screen.queryByRole('link', { name: 'SLA' })).not.toBeInTheDocument();
  });

  it('turns business hours on, edits days, times and holidays, and reports the recalculation', async () => {
    const save = vi.fn().mockImplementation((request) =>
      Promise.resolve({ calendar: aCalendar({ ...request }), recalculatedTickets: 7 }),
    );
    const slaCalendar = fakeSlaCalendarApi({ get: vi.fn().mockResolvedValue(aCalendar()), save });
    renderApp('/sla', { role: 'MANAGER', slaCalendar });

    const form = await screen.findByRole('form', { name: 'Mesai saatleri' });
    expect(within(form).getByLabelText('Başlangıç')).toBeDisabled(); // off: the rest is inactive
    await userEvent.click(within(form).getByRole('checkbox', { name: 'Mesai saatlerini kullan' }));
    await userEvent.click(within(form).getByRole('checkbox', { name: 'Cumartesi' }));
    await userEvent.clear(within(form).getByLabelText('Bitiş'));
    await userEvent.type(within(form).getByLabelText('Bitiş'), '17:30');

    const holidays = within(form).getByRole('list', { name: 'Tatiller' });
    await userEvent.click(within(holidays).getByRole('button', { name: 'Yılbaşı tatilini kaldır' }));
    await userEvent.type(within(form).getByLabelText('Tarih'), '2026-12-31');
    await userEvent.type(within(form).getByLabelText('Ad'), 'Yılbaşı arifesi');
    await userEvent.click(within(form).getByRole('button', { name: 'Tatil ekle' }));
    const listed = within(holidays).getAllByRole('listitem');
    expect(listed).toHaveLength(2);
    expect(listed[0]).toHaveTextContent('Cumhuriyet Bayramı');
    expect(listed[1]).toHaveTextContent('Yılbaşı arifesi');

    await userEvent.click(within(form).getByRole('button', { name: 'Kaydet' }));

    expect(save).toHaveBeenCalledWith({
      enabled: true,
      zoneId: 'Europe/Istanbul',
      workDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
      start: '09:00',
      end: '17:30',
      holidays: [
        { date: '2026-10-29', name: 'Cumhuriyet Bayramı' },
        { date: '2026-12-31', name: 'Yılbaşı arifesi' },
      ],
    });
    expect(await screen.findByRole('status')).toHaveTextContent('7 açık talebin süreleri yeniden hesaplandı');
  });

  it('refuses an empty week or an end before the start without calling the server', async () => {
    const save = vi.fn();
    const slaCalendar = fakeSlaCalendarApi({
      get: vi.fn().mockResolvedValue(aCalendar({ enabled: true })),
      save,
    });
    renderApp('/sla', { role: 'MANAGER', slaCalendar });

    const form = await screen.findByRole('form', { name: 'Mesai saatleri' });
    const submit = within(form).getByRole('button', { name: 'Kaydet' });
    await userEvent.clear(within(form).getByLabelText('Bitiş'));
    await userEvent.type(within(form).getByLabelText('Bitiş'), '08:00');
    expect(within(form).getByText('Mesai bitişi başlangıçtan sonra olmalı.')).toBeInTheDocument();
    expect(submit).toBeDisabled();

    await userEvent.clear(within(form).getByLabelText('Bitiş'));
    await userEvent.type(within(form).getByLabelText('Bitiş'), '18:00');
    for (const day of ['Pazartesi', 'Salı', 'Çarşamba', 'Perşembe', 'Cuma']) {
      await userEvent.click(within(form).getByRole('checkbox', { name: day }));
    }
    expect(within(form).getByText('En az bir çalışma günü seçin.')).toBeInTheDocument();
    expect(submit).toBeDisabled();
    expect(save).not.toHaveBeenCalled();
  });

  it('does not add the same holiday date twice', async () => {
    const slaCalendar = fakeSlaCalendarApi({ get: vi.fn().mockResolvedValue(aCalendar()) });
    renderApp('/sla', { role: 'MANAGER', slaCalendar });

    const form = await screen.findByRole('form', { name: 'Mesai saatleri' });
    await userEvent.type(within(form).getByLabelText('Tarih'), '2026-10-29');
    await userEvent.type(within(form).getByLabelText('Ad'), 'Başka ad');
    await userEvent.click(within(form).getByRole('button', { name: 'Tatil ekle' }));

    expect(within(form).getByText('Bu tarih zaten listede.')).toBeInTheDocument();
    expect(within(form).getAllByRole('listitem')).toHaveLength(2);
  });
});
