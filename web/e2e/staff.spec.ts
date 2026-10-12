import type { Page } from '@playwright/test';
import { createTicket, expect, test, ticketHeader, uniqueTitle } from './fixtures.ts';

function bell(page: Page) {
  return page.getByRole('button', { name: /^Bildirimler/ });
}

function facts(page: Page) {
  return page.locator('dl');
}

test('agent claims from the unassigned queue and hands over to the manager', async ({ as }) => {
  const customer = await as('customer1');
  const title = uniqueTitle('kuyruk');
  const id = await createTicket(customer, title);

  const agent = await as('agent1');
  await agent.goto(`/tickets?assignee=unassigned&q=${encodeURIComponent(title)}`);
  await expect(agent.getByRole('button', { name: 'Atanmamışlar' })).toHaveAttribute('aria-pressed', 'true');
  await agent.getByRole('link', { name: title }).click();

  await agent.getByRole('button', { name: 'Üstlen' }).click();
  await expect(facts(agent)).toContainText('Siz');
  await expect(ticketHeader(agent)).toContainText('Yeni'); // claiming does not change the status

  // The ticket is now in the agent's own queue.
  await agent.goto(`/tickets?assignee=me&sort=sla&q=${encodeURIComponent(title)}`);
  await expect(agent.getByRole('link', { name: title })).toBeVisible();

  await agent.goto(`/tickets/${id}`);
  const panel = agent.getByRole('region', { name: 'Atama' });
  await panel.getByLabel('Devredilecek kişi').selectOption({ label: 'Manager One · yönetici' });
  await panel.getByRole('button', { name: 'Devret' }).click();
  await expect(facts(agent)).toContainText('Manager One');
  // No longer theirs: the agent cannot reassign it any more.
  await expect(agent.getByRole('region', { name: 'Atama' })).toHaveCount(0);
  await expect(agent.getByRole('complementary')).toContainText('Atama: Agent One → Manager One');

  const manager = await as('manager1');
  await manager.goto('/tickets');
  await bell(manager).click();
  await expect(
    manager.getByRole('dialog', { name: 'Bildirimler' }).getByRole('button', { name: title }).first(),
  ).toContainText('talebi size atadı');
});

test('manager raises the priority with a reason; the customer does not see the staff log', async ({ as }) => {
  const customer = await as('customer1');
  const title = uniqueTitle('öncelik');
  const id = await createTicket(customer, title); // Orta: 1 day

  const manager = await as('manager1');
  await manager.goto(`/tickets/${id}`);
  await expect(facts(manager)).toContainText('1 g');
  await manager.getByRole('button', { name: 'Önceliği / kategoriyi değiştir' }).click();
  const form = manager.getByRole('form', { name: 'Talebi düzenle' });
  await form.getByLabel('Öncelik').selectOption({ label: 'Kritik' });
  await form.getByLabel('Kategori').selectOption({ label: 'Ağ' });
  await expect(form.getByRole('button', { name: 'Kaydet' })).toBeDisabled();
  await form.getByLabel('Öncelik değişikliğinin gerekçesi').fill('Tüm ofis etkileniyor');
  await form.getByRole('button', { name: 'Kaydet' }).click();

  await expect(ticketHeader(manager)).toContainText('Kritik');
  await expect(facts(manager)).toContainText('4 sa'); // SLA target follows the priority
  await expect(facts(manager)).toContainText('Ağ');
  const side = manager.getByRole('complementary');
  await expect(side).toContainText('Öncelik: Orta → Kritik');
  await expect(side).toContainText('Tüm ofis etkileniyor');
  await expect(side).toContainText('Kategori: Diğer → Ağ');

  await customer.reload();
  await expect(ticketHeader(customer)).toContainText('Kritik');
  await expect(customer.getByRole('complementary')).not.toContainText('Tüm ofis etkileniyor');
  await expect(customer.getByRole('button', { name: 'Önceliği / kategoriyi değiştir' })).toHaveCount(0);
});
