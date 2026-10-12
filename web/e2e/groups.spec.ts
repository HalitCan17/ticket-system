import type { Page } from '@playwright/test';
import { createTicket, expect, test, uniqueTitle } from './fixtures.ts';

/** Sets the members and the assignment mode of a group on the Groups page. */
async function configureGroup(manager: Page, group: string, members: string[], mode: string) {
  await manager.goto('/groups');
  const card = manager.getByRole('listitem', { name: group });
  await card.getByRole('button', { name: 'Üyeler' }).click();
  const form = card.getByRole('form', { name: `${group} üyeleri` });
  for (const box of await form.getByRole('checkbox').all()) {
    const name = (await box.evaluate((el) => el.parentElement?.textContent ?? '')).replace(' · yönetici', '');
    await box.setChecked(members.includes(name.trim()));
  }
  await form.getByRole('button', { name: 'Üyeleri kaydet' }).click();
  await expect(card.getByRole('form')).toHaveCount(0);

  await card.getByRole('button', { name: 'Düzenle' }).click();
  const edit = card.getByRole('form', { name: `${group} düzenle` });
  await edit.getByRole('radio', { name: mode }).check();
  await edit.getByRole('button', { name: 'Kaydet' }).click();
  await expect(card).toContainText(mode);
}

test('a network ticket is routed to the network team and assigned round-robin', async ({ as }) => {
  const manager = await as('manager1');
  await configureGroup(manager, 'Ağ ekibi', ['Agent One'], 'Üyelere sırayla ata');
  await expect(manager.getByRole('listitem', { name: 'Ağ ekibi' })).toContainText('Agent One');

  try {
    const customer = await as('customer1');
    const title = uniqueTitle('yönlendirme');
    const id = await createTicket(customer, title, 'VPN bağlanmıyor.', 'Ağ');
    const facts = customer.locator('dl');
    await expect(facts).toContainText('Ağ ekibi');
    await expect(facts).toContainText('Agent One');

    const agent = await as('agent1');
    await agent.goto('/tickets');
    await agent.getByRole('button', { name: /^Bildirimler/ }).click();
    await expect(
      agent.getByRole('dialog', { name: 'Bildirimler' }).getByRole('button', { name: title }).first(),
    ).toContainText('talebi size atadı');

    await agent.goto('/tickets');
    await agent.getByRole('button', { name: 'Grubumun talepleri' }).click();
    await agent.getByLabel('Ara').fill(title);
    await expect(agent.getByRole('link', { name: title })).toBeVisible();

    await agent.goto(`/tickets/${id}`);
    await expect(agent.getByRole('complementary')).toContainText('Atama: atanmamış → Agent One');
  } finally {
    // Leave the shared stack as it was for the other tests.
    await configureGroup(manager, 'Ağ ekibi', [], 'Otomatik atama yok (grup kuyruğunda bekler)');
  }
});
