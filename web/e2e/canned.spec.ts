import { createTicket, expect, test, uniqueTitle } from './fixtures.ts';

test('manager adds a canned response, agent uses it, customer reads the filled-in text', async ({ as }) => {
  const templateTitle = uniqueTitle('şablon');

  const manager = await as('manager1');
  await manager.goto('/tickets');
  await manager.getByRole('link', { name: 'Hazır yanıtlar' }).click();
  await manager.getByRole('button', { name: '+ Yeni hazır yanıt' }).click();
  const form = manager.getByRole('form', { name: 'Yeni hazır yanıt' });
  await form.getByLabel('Başlık').fill(templateTitle);
  await form.getByLabel('Metin').fill('Merhaba {müşteri}, {talep} için VPN profilinizi yeniledik. Saygılar, {ben}');
  await form.getByRole('button', { name: 'Kaydet' }).click();
  await expect(manager.getByText(templateTitle)).toBeVisible();

  const customer = await as('customer1');
  const id = await createTicket(customer, uniqueTitle('hazır yanıt'));

  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  const commentForm = agent.locator('form').filter({ has: agent.locator('#comment-body') });
  await commentForm.getByLabel('Hazır yanıt').selectOption({ label: templateTitle });
  const expected = `Merhaba Customer One, #${id} için VPN profilinizi yeniledik. Saygılar, Agent One`;
  await expect(commentForm.getByLabel('Yorum', { exact: true })).toHaveValue(expected);
  await expect(commentForm.getByRole('radio', { name: 'Müşteriye açık' })).toBeChecked();
  await commentForm.getByRole('button', { name: 'Yorum ekle' }).click();
  await expect(agent.getByText(expected)).toBeVisible();

  await customer.reload();
  await expect(customer.getByText(expected)).toBeVisible();
  // Customers never get the template picker.
  await expect(customer.getByLabel('Hazır yanıt')).toHaveCount(0);

  // Agents can use templates but not manage them.
  await agent.goto('/canned-responses');
  await expect(agent.getByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeVisible();

  await manager.reload();
  manager.once('dialog', (dialog) => void dialog.accept());
  await manager.getByRole('button', { name: `${templateTitle} sil` }).click();
  await expect(manager.getByText(templateTitle)).toHaveCount(0);
  // The comment written from it is unaffected.
  await customer.reload();
  await expect(customer.getByText(expected)).toBeVisible();
});
