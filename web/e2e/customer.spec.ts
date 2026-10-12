import type { Page } from '@playwright/test';
import { createTicket, expect, test, ticketHeader, transition, uniqueTitle } from './fixtures.ts';

function bell(page: Page) {
  return page.getByRole('button', { name: /^Bildirimler/ });
}

test('customer opens a categorized ticket, finds it by search and rates the resolution', async ({ as }) => {
  const customer = await as('customer1');
  const title = uniqueTitle('puan');
  const id = await createTicket(customer, title, 'Uçtan uca test talebi.', 'Ağ');
  await expect(customer.locator('dl')).toContainText('Ağ');

  // Search by title and by #id on the list.
  await customer.goto('/tickets');
  await customer.getByLabel('Ara').fill(title);
  await expect(customer).toHaveURL(/q=/);
  await expect(customer.getByRole('link', { name: title })).toBeVisible();
  await expect(customer.locator('tbody tr')).toHaveCount(1);
  await customer.getByLabel('Ara').fill(`#${id}`);
  await expect(customer.locator('tbody tr')).toHaveCount(1);
  await expect(customer.locator('tbody tr')).toContainText('Ağ');

  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  await transition(agent, 'İşleme al');
  await transition(agent, 'Çözüldü olarak işaretle', 'Modem yeniden başlatıldı.');
  await expect(ticketHeader(agent)).toContainText('Çözüldü');

  await customer.goto(`/tickets/${id}`);
  await customer.getByRole('radio', { name: '5 – Çok iyi' }).check();
  await customer.getByLabel('Yorum (isteğe bağlı)').fill('Çok hızlı çözüldü.');
  await customer.getByRole('button', { name: 'Puanı gönder' }).click();
  const rating = customer.getByRole('region', { name: 'Memnuniyet puanı' });
  await expect(rating).toContainText('5/5 · Çok iyi');
  await expect(rating).toContainText('Çok hızlı çözüldü.');
  await expect(customer.getByRole('button', { name: 'Puanı gönder' })).toHaveCount(0);

  // The assignee is told and sees the rating on the ticket.
  await agent.goto('/tickets');
  await bell(agent).click();
  await expect(
    agent.getByRole('dialog', { name: 'Bildirimler' }).getByRole('button', { name: title }).first(),
  ).toContainText('çözümü puanladı');
  await agent.goto(`/tickets/${id}`);
  await expect(agent.getByRole('region', { name: 'Memnuniyet puanı' })).toContainText('5/5');
});

test('customer withdraws a new ticket; it moves to the closed tab', async ({ as }) => {
  const customer = await as('customer1');
  const title = uniqueTitle('geri çek');
  const id = await createTicket(customer, title);

  customer.once('dialog', (dialog) => void dialog.accept());
  await customer.getByRole('button', { name: 'Talebi geri çek' }).click();
  await expect(ticketHeader(customer)).toContainText('Kapalı');
  await expect(customer.getByText('Kapalı taleplere yorum eklenemez.')).toBeVisible();
  // Nothing was resolved, so there is nothing to rate.
  await expect(customer.getByRole('button', { name: 'Puanı gönder' })).toHaveCount(0);

  // Staff can no longer start it.
  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  await expect(ticketHeader(agent)).toContainText('Kapalı');
  await expect(agent.getByRole('button', { name: 'İşleme al' })).toHaveCount(0);

  await customer.goto(`/tickets?q=${encodeURIComponent(title)}`);
  await expect(customer.getByRole('tab', { name: 'Açık' })).toHaveAttribute('aria-selected', 'true');
  await expect(customer.getByText('Aramaya uyan talep yok.')).toBeVisible();
  await customer.getByRole('tab', { name: 'Kapanmış' }).click();
  await expect(customer.getByRole('link', { name: title })).toBeVisible();
});
