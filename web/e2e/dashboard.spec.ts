import { createTicket, expect, test, uniqueTitle } from './fixtures.ts';

test('manager reads the dashboard and opens an agent’s queue from the team table', async ({ as }) => {
  // Give agent1 an open ticket so the team row has work behind it.
  const customer = await as('customer1');
  const title = uniqueTitle('panel');
  const id = await createTicket(customer, title);
  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  await agent.getByRole('button', { name: 'Üstlen' }).click();
  await expect(agent.locator('dl')).toContainText('Siz');

  const manager = await as('manager1');
  await manager.goto('/tickets');
  await manager.getByRole('link', { name: 'Panel' }).click();
  await expect(manager.getByRole('heading', { name: 'Panel' })).toBeVisible();
  await expect(manager.getByRole('link', { name: /^Açık talep: \d+$/ })).toBeVisible();
  await expect(manager.getByRole('list', { name: 'Statü dağılımı' }).getByRole('listitem')).toHaveCount(5);

  await manager.getByRole('button', { name: 'Son 7 gün' }).click();
  await expect(manager).toHaveURL(/days=7/);
  await expect(manager.getByRole('heading', { name: 'Son 7 gün' })).toBeVisible();

  const agentRow = manager.getByRole('row').filter({ has: manager.getByRole('link', { name: 'Agent One' }) });
  await expect(agentRow).toBeVisible();
  await agentRow.getByRole('link', { name: 'Agent One' }).click();

  await expect(manager).toHaveURL(/assignee=/);
  await expect(manager.getByText(/Kuyruk: Agent One/)).toBeVisible();
  await manager.getByLabel('Ara').fill(title);
  await expect(manager.getByRole('link', { name: title })).toBeVisible();
});
