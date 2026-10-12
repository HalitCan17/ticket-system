import { createTicket, expect, test, uniqueTitle } from './fixtures.ts';

test('the first response clock runs until staff answer the customer', async ({ as }) => {
  const customer = await as('customer1');
  const id = await createTicket(customer, uniqueTitle('ilk yanıt'));
  const facts = customer.locator('dl');
  await expect(facts).toContainText(/İlk yanıt\s*\d+ sa \d+ dk kaldı|İlk yanıt\s*\d+ sa kaldı/);

  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  // An internal note is not an answer to the customer.
  const form = agent.locator('form').filter({ has: agent.locator('#comment-body') });
  await form.getByLabel('Yorum', { exact: true }).fill('Dahili: loglara bakılacak.');
  await form.getByRole('radio', { name: 'Dahili (yalnızca ekip)' }).check();
  await form.getByRole('button', { name: 'Yorum ekle' }).click();
  // Wait for the saved comment in the list (getByText would also match the text box) and for the form to clear.
  await expect(agent.getByRole('listitem').filter({ hasText: 'Dahili: loglara bakılacak.' })).toBeVisible();
  await expect(form.getByLabel('Yorum', { exact: true })).toHaveValue('');
  await expect(agent.locator('dl')).toContainText('kaldı');

  await form.getByLabel('Yorum', { exact: true }).fill('Merhaba, talebinizi inceliyoruz.');
  await form.getByRole('radio', { name: 'Müşteriye açık' }).check();
  await form.getByRole('button', { name: 'Yorum ekle' }).click();
  await expect(agent.getByRole('listitem').filter({ hasText: 'Merhaba, talebinizi inceliyoruz.' })).toBeVisible();
  await expect(form.getByLabel('Yorum', { exact: true })).toHaveValue('');

  await agent.reload();
  await expect(agent.locator('dl')).toContainText(/içinde yanıtlandı/);
  await customer.reload();
  await expect(facts).toContainText(/içinde yanıtlandı/);
  await expect(facts).not.toContainText('(geç)');
});
