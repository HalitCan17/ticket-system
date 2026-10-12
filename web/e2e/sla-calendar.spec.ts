import type { Page } from '@playwright/test';
import { createTicket, expect, test, uniqueTitle } from './fixtures.ts';

const HOLIDAY_NAME = 'E2E deneme tatili';
const HOLIDAY_DATE = '2031-03-14';

async function openSettings(manager: Page) {
  await manager.goto('/sla');
  const form = manager.getByRole('form', { name: 'Mesai saatleri' });
  await expect(form).toBeVisible();
  return form;
}

test('manager turns business hours on with an extra holiday; the settings persist and can be undone', async ({ as }) => {
  test.setTimeout(60_000);
  const manager = await as('manager1');
  let form = await openSettings(manager);
  await expect(manager.getByRole('table')).toContainText('Kritik');
  await expect(form.getByRole('checkbox', { name: 'Mesai saatlerini kullan' })).not.toBeChecked();

  try {
    await form.getByRole('checkbox', { name: 'Mesai saatlerini kullan' }).check();
    await form.getByLabel('Bitiş').fill('17:00');
    await form.getByLabel('Tarih').fill(HOLIDAY_DATE);
    await form.getByLabel('Ad').fill(HOLIDAY_NAME);
    await form.getByRole('button', { name: 'Tatil ekle' }).click();
    await expect(form.getByRole('list', { name: 'Tatiller' })).toContainText(HOLIDAY_NAME);
    await form.getByRole('button', { name: 'Kaydet' }).click();
    await expect(manager.getByRole('status')).toContainText('Kaydedildi');

    // Persisted: a fresh load shows the same settings.
    form = await openSettings(manager);
    await expect(form.getByRole('checkbox', { name: 'Mesai saatlerini kullan' })).toBeChecked();
    await expect(form.getByLabel('Bitiş')).toHaveValue('17:00');
    await expect(form.getByRole('list', { name: 'Tatiller' })).toContainText(HOLIDAY_NAME);

    // A ticket opened now gets its due moment from the calendar; the page still shows an SLA end.
    const customer = await as('customer1');
    await createTicket(customer, uniqueTitle('mesai'));
    await expect(customer.locator('dl')).toContainText('SLA bitişi');
  } finally {
    // Leave the shared stack as it was for the other tests.
    form = await openSettings(manager);
    // The time fields are only editable while business hours are on: restore them first, then switch off.
    await form.getByLabel('Bitiş').fill('18:00');
    await form.getByRole('checkbox', { name: 'Mesai saatlerini kullan' }).uncheck();
    const remove = form.getByRole('button', { name: `${HOLIDAY_NAME} tatilini kaldır` });
    if (await remove.count()) {
      await remove.click();
    }
    await form.getByRole('button', { name: 'Kaydet' }).click();
    await expect(manager.getByRole('status')).toContainText('Kaydedildi');
  }

  form = await openSettings(manager);
  await expect(form.getByRole('checkbox', { name: 'Mesai saatlerini kullan' })).not.toBeChecked();
  await expect(form.getByRole('list', { name: 'Tatiller' })).not.toContainText(HOLIDAY_NAME);
});

test('agents see no SLA settings page', async ({ as }) => {
  const agent = await as('agent1');
  await agent.goto('/sla');
  await expect(agent.getByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeVisible();
  await expect(agent.getByRole('link', { name: 'SLA' })).toHaveCount(0);
});
