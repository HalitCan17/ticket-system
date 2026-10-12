import type { Page } from '@playwright/test';
import { expect, test, uniqueTitle } from './fixtures.ts';

async function writeArticle(page: Page, title: string, body: string, visibility: string): Promise<number> {
  await page.goto('/kb/new');
  const form = page.getByRole('form', { name: 'Makale' });
  await form.getByLabel('Başlık').fill(title);
  await form.getByLabel('İçerik').fill(body);
  await form.getByLabel('Kategori').selectOption({ label: 'Ağ' });
  await form.getByLabel('Görünürlük').selectOption({ label: visibility });
  await form.getByRole('button', { name: 'Kaydet' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText(title);
  return Number(new URL(page.url()).pathname.split('/').pop());
}

test('agent writes articles; the customer finds the public one, votes, and sees no internal one', async ({ as }) => {
  const publicTitle = uniqueTitle('kb açık');
  const internalTitle = uniqueTitle('kb iç');
  const created: number[] = [];

  const agent = await as('agent1');
  try {
    created.push(await writeArticle(agent, publicTitle, 'Önce bağlantıyı doğrulayın.', 'Herkese açık'));
    created.push(await writeArticle(agent, internalTitle, 'Yalnızca ekip için not.', 'Yalnızca ekip'));

    const customer = await as('customer1');
    await customer.goto('/kb');
    await expect(customer.getByRole('link', { name: '+ Yeni makale' })).toHaveCount(0);
    await customer.getByLabel('Ara').fill('E2E kb');
    const list = customer.getByRole('list', { name: 'Makaleler' });
    await expect(list.getByRole('link', { name: publicTitle })).toBeVisible();
    await expect(customer.getByText(internalTitle)).toHaveCount(0);

    await list.getByRole('link', { name: publicTitle }).click();
    await expect(customer.getByText('Önce bağlantıyı doğrulayın.')).toBeVisible();
    await expect(customer.getByRole('link', { name: 'Düzenle' })).toHaveCount(0);
    await customer.getByRole('button', { name: /^Evet/ }).click();
    await expect(customer.getByRole('button', { name: /^Evet/ })).toHaveAttribute('aria-pressed', 'true');
    await customer.reload();
    await expect(customer.getByRole('button', { name: /^Evet/ })).toHaveAttribute('aria-pressed', 'true');

    // An article the customer may not read is simply missing.
    await customer.goto(`/kb/${created[1]}`);
    await expect(customer.getByRole('alert')).toBeVisible();
  } finally {
    const manager = await as('manager1');
    for (const id of created) {
      await manager.goto(`/kb/${id}`);
      manager.once('dialog', (dialog) => void dialog.accept());
      await manager.getByRole('button', { name: 'Sil' }).click();
      await expect(manager).toHaveURL(/\/kb$/);
    }
  }
});

test('a customer typing a new ticket title is offered the matching public article', async ({ as }) => {
  const title = uniqueTitle('kb öneri');
  const manager = await as('manager1');
  const id = await writeArticle(manager, title, 'Çözüm adımları burada.', 'Herkese açık');
  try {
    const customer = await as('customer1');
    await customer.goto('/tickets/new');
    await customer.getByLabel('Başlık').fill(title);
    const suggestions = customer.getByRole('complementary', { name: 'Önerilen makaleler' });
    await expect(suggestions.getByRole('link', { name: title })).toBeVisible();
    // The form is untouched: the customer can still open the ticket.
    await expect(customer.getByRole('button', { name: 'Talep aç' })).toBeEnabled();
  } finally {
    await manager.goto(`/kb/${id}`);
    manager.once('dialog', (dialog) => void dialog.accept());
    await manager.getByRole('button', { name: 'Sil' }).click();
    await expect(manager).toHaveURL(/\/kb$/);
  }
});

test('an agent links an article from a comment and the customer can open it', async ({ as }) => {
  const title = uniqueTitle('kb bağlantı');
  const manager = await as('manager1');
  const id = await writeArticle(manager, title, 'Bağlantılı çözüm.', 'Herkese açık');
  try {
    const customer = await as('customer1');
    await customer.goto('/tickets/new');
    const ticketTitle = uniqueTitle('kb talep');
    await customer.getByLabel('Başlık').fill(ticketTitle);
    await customer.getByLabel('Açıklama').fill('Makale bağlantısı denemesi.');
    await customer.getByRole('button', { name: 'Talep aç' }).click();
    await expect(customer.getByRole('heading', { level: 1 })).toContainText(ticketTitle);
    const ticketUrl = customer.url();

    const agent = await as('agent1');
    await agent.goto(ticketUrl);
    const commentForm = agent.locator('form').filter({ has: agent.locator('#comment-body') });
    await commentForm.getByLabel('Bilgi bankası makalesi').selectOption({ label: title });
    await commentForm.getByRole('radio', { name: 'Müşteriye açık' }).check();
    const link = `${new URL(ticketUrl).origin}/kb/${id}`;
    await expect(commentForm.getByLabel('Yorum', { exact: true })).toHaveValue(`${title}: ${link}`);
    await commentForm.getByRole('button', { name: 'Yorum ekle' }).click();
    await expect(agent.getByRole('listitem').filter({ hasText: link })).toBeVisible();

    await customer.reload();
    await expect(customer.getByRole('listitem').filter({ hasText: link })).toBeVisible();
    // Customers never get the article picker.
    await expect(customer.getByLabel('Bilgi bankası makalesi')).toHaveCount(0);
  } finally {
    await manager.goto(`/kb/${id}`);
    manager.once('dialog', (dialog) => void dialog.accept());
    await manager.getByRole('button', { name: 'Sil' }).click();
    await expect(manager).toHaveURL(/\/kb$/);
  }
});
