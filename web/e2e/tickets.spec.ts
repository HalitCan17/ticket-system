import { createTicket, expect, test, ticketHeader, transition, uniqueTitle } from './fixtures.ts';
import { loginWithKeycloak } from './users.ts';

test('an anonymous visit is sent to Keycloak and comes back signed in', async ({ page }) => {
  await page.goto('/tickets');
  await loginWithKeycloak(page, 'customer1');
  await expect(page).toHaveURL(/localhost:3000\/tickets/);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});

test("a customer cannot open another customer's ticket", async ({ as }) => {
  const owner = await as('customer1');
  const title = uniqueTitle('erişim');
  const id = await createTicket(owner, title);

  const other = await as('customer2');
  await other.goto(`/tickets/${id}`);
  await expect(other.getByRole('heading', { name: 'Talep bulunamadı' })).toBeVisible();
  await other.goto('/tickets');
  await expect(other.getByRole('link', { name: title })).toHaveCount(0);
});

test('ticket lifecycle: work, wait, resolve, object, resolve again, confirm', async ({ as }) => {
  const customer = await as('customer1');
  const title = uniqueTitle('yaşam döngüsü');
  const id = await createTicket(customer, title);
  await expect(ticketHeader(customer)).toContainText('Yeni');

  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  await transition(agent, 'İşleme al');
  await expect(ticketHeader(agent)).toContainText('İşlemde');

  await transition(agent, 'Müşteriden bilgi bekle');
  await expect(ticketHeader(agent)).toContainText('Müşteri bekleniyor');
  await expect(ticketHeader(agent)).toContainText('Duraklatıldı');

  await transition(agent, 'İşleme devam et');
  await expect(ticketHeader(agent)).toContainText('İşlemde');

  // The resolution note is required.
  await agent.getByRole('button', { name: 'Çözüldü olarak işaretle' }).click();
  const resolveForm = agent.locator('form').filter({ has: agent.locator('#transition-note') });
  await expect(resolveForm.getByRole('button', { name: 'Çözüldü olarak işaretle' })).toBeDisabled();
  await resolveForm.locator('#transition-note').fill('Yazıcı sürücüsü güncellendi.');
  await resolveForm.getByRole('button', { name: 'Çözüldü olarak işaretle' }).click();
  await expect(ticketHeader(agent)).toContainText('Çözüldü');

  await customer.reload();
  await expect(customer.getByText('Yazıcı sürücüsü güncellendi.').first()).toBeVisible();
  await expect(customer.getByRole('status')).toContainText('otomatik kapanacak');
  await transition(customer, 'Çözüme itiraz et', 'Sorun devam ediyor.');
  await expect(ticketHeader(customer)).toContainText('İşlemde');

  await agent.reload();
  await transition(agent, 'Çözüldü olarak işaretle', 'Yazıcı yeniden kuruldu.');
  await expect(ticketHeader(agent)).toContainText('Çözüldü');

  await customer.reload();
  await transition(customer, 'Onayla ve kapat');
  await expect(ticketHeader(customer)).toContainText('Kapalı');
  await expect(customer.getByText('Kapalı taleplere yorum eklenemez.')).toBeVisible();

  // History, newest last: every step with its note.
  const history = customer.getByRole('list').filter({ hasText: 'Sorun devam ediyor.' });
  await expect(history.getByRole('listitem')).toHaveText([
    /Yeni/,
    /İşlemde/,
    /Müşteri bekleniyor/,
    /İşlemde/,
    /Çözüldü.*Yazıcı sürücüsü güncellendi\./s,
    /İşlemde.*Sorun devam ediyor\./s,
    /Çözüldü.*Yazıcı yeniden kuruldu\./s,
    /Kapalı/,
  ]);
});
