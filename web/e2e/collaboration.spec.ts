import { readFile } from 'node:fs/promises';
import type { Page } from '@playwright/test';
import { createTicket, expect, test, uniqueTitle } from './fixtures.ts';

async function comment(page: Page, body: string, visibility?: 'Dahili (yalnızca ekip)' | 'Müşteriye açık') {
  // The attachment form has its own visibility choice; stay inside the comment form.
  const form = page.locator('form').filter({ has: page.locator('#comment-body') });
  await form.getByLabel('Yorum', { exact: true }).fill(body);
  if (visibility) {
    await form.getByRole('radio', { name: visibility }).check();
  }
  await form.getByRole('button', { name: 'Yorum ekle' }).click();
  await expect(page.getByText(body)).toBeVisible();
  await expect(form.getByLabel('Yorum', { exact: true })).toHaveValue('');
}

function bell(page: Page) {
  return page.getByRole('button', { name: /^Bildirimler/ });
}

async function unreadCount(page: Page): Promise<number> {
  const label = (await bell(page).getAttribute('aria-label')) ?? '';
  return Number(/(\d+) okunmamış/.exec(label)?.[1] ?? 0);
}

test('internal comments stay hidden from the customer', async ({ as }) => {
  const customer = await as('customer1');
  const id = await createTicket(customer, uniqueTitle('yorum'));

  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  await comment(agent, 'Dahili: sunucu loglarına bakılacak.', 'Dahili (yalnızca ekip)');
  await comment(agent, 'Merhaba, inceliyoruz.', 'Müşteriye açık');

  await customer.reload();
  await expect(customer.getByText('Merhaba, inceliyoruz.')).toBeVisible();
  await expect(customer.getByText('Dahili: sunucu loglarına bakılacak.')).toHaveCount(0);
  // The customer form has no visibility choice.
  await expect(customer.getByRole('radio')).toHaveCount(0);
});

test('a reply notifies the customer and the notification opens the ticket', async ({ as }) => {
  const customer = await as('customer1');
  const title = uniqueTitle('bildirim');
  const id = await createTicket(customer, title);
  await expect(bell(customer)).toBeVisible();
  const before = await unreadCount(customer);

  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  await comment(agent, 'Yanıtımız ektedir.', 'Müşteriye açık');

  // The bell polls every 30 s; a reload fetches the count at once.
  await customer.goto('/tickets');
  await expect.poll(() => unreadCount(customer)).toBe(before + 1);

  await bell(customer).click();
  const item = customer.getByRole('dialog', { name: 'Bildirimler' }).getByRole('button', { name: title });
  await expect(item).toContainText('yorum yazdı');
  await item.click();
  await expect(customer).toHaveURL(new RegExp(`/tickets/${id}$`));
  await expect(customer.getByRole('heading', { level: 1 })).toContainText(title);
  await expect.poll(() => unreadCount(customer)).toBe(before);
});

test('attachments: upload, download, internal visibility, type check, delete', async ({ as }) => {
  const customer = await as('customer1');
  const id = await createTicket(customer, uniqueTitle('ek'));
  const content = 'hata kaydı satır 1\nsatır 2\n';

  await customer.getByLabel('Dosya ekle').setInputFiles({
    name: 'hata-kaydı.log',
    mimeType: 'text/plain',
    buffer: Buffer.from(content, 'utf8'),
  });
  await customer.getByRole('button', { name: 'Yükle' }).click();
  const customerFile = customer.getByRole('button', { name: 'hata-kaydı.log', exact: true });
  await expect(customerFile).toBeVisible();

  const [download] = await Promise.all([customer.waitForEvent('download'), customerFile.click()]);
  expect(download.suggestedFilename()).toBe('hata-kaydı.log');
  expect(await readFile(await download.path(), 'utf8')).toBe(content);

  // A renamed executable is refused by the browser check before upload.
  await customer.getByLabel('Dosya ekle').setInputFiles({
    name: 'kurulum.exe',
    mimeType: 'application/octet-stream',
    buffer: Buffer.from('MZ'),
  });
  await expect(customer.getByRole('alert')).toContainText('Bu dosya türü eklenemez');
  await expect(customer.getByRole('button', { name: 'Yükle' })).toBeDisabled();

  // Staff upload defaults to internal: the customer must not see it.
  const agent = await as('agent1');
  await agent.goto(`/tickets/${id}`);
  await agent.getByLabel('Dosya ekle').setInputFiles({
    name: 'teshis.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('dahili teşhis'),
  });
  await agent.getByRole('button', { name: 'Yükle' }).click();
  await expect(agent.getByRole('button', { name: 'teshis.txt', exact: true })).toBeVisible();
  // The agent did not upload the customer's file, so cannot delete it.
  await expect(agent.getByRole('button', { name: 'hata-kaydı.log sil' })).toHaveCount(0);

  await customer.reload();
  await expect(customerFile).toBeVisible();
  await expect(customer.getByText('teshis.txt')).toHaveCount(0);

  customer.once('dialog', (dialog) => void dialog.accept());
  await customer.getByRole('button', { name: 'hata-kaydı.log sil' }).click();
  await expect(customerFile).toHaveCount(0);
  await expect(customer.getByText('Ek yok.')).toBeVisible();
});
