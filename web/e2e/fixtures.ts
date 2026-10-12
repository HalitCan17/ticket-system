import { test as base, expect, type Page } from '@playwright/test';
import { storageStatePath, type TestUser } from './users.ts';

interface Fixtures {
  /** Opens a page signed in as the given user, in its own browser context. */
  as: (user: TestUser) => Promise<Page>;
}

export const test = base.extend<Fixtures>({
  as: async ({ browser }, use) => {
    const contexts: Awaited<ReturnType<typeof browser.newContext>>[] = [];
    await use(async (user) => {
      const context = await browser.newContext({ storageState: storageStatePath(user) });
      contexts.push(context);
      return context.newPage();
    });
    await Promise.all(contexts.map((c) => c.close()));
  },
});

export { expect };

/** Unique per run, so tests do not depend on data left by earlier runs. */
export function uniqueTitle(prefix: string): string {
  return `E2E ${prefix} ${Date.now().toString(36)}`;
}

/** Creates a ticket through the form and returns its id; leaves the page on the ticket. */
export async function createTicket(
  page: Page,
  title: string,
  description = 'Uçtan uca test talebi.',
  category?: string,
): Promise<number> {
  await page.goto('/tickets/new');
  await page.getByLabel('Başlık').fill(title);
  await page.getByLabel('Açıklama').fill(description);
  if (category) {
    await page.getByLabel('Kategori').selectOption({ label: category });
  }
  await page.getByRole('button', { name: 'Talep aç' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText(title);
  const id = Number(new URL(page.url()).pathname.split('/').pop());
  expect(id).toBeGreaterThan(0);
  return id;
}

/** Status, priority and SLA badges in the ticket header. */
export function ticketHeader(page: Page) {
  return page.locator('article header');
}

/** Clicks a transition button; when it asks for a note, fills it and confirms. */
export async function transition(page: Page, action: string, note?: string): Promise<void> {
  await page.getByRole('button', { name: action }).click();
  if (note !== undefined) {
    const form = page.locator('form').filter({ has: page.locator('#transition-note') });
    await form.locator('#transition-note').fill(note);
    await form.getByRole('button', { name: action }).click();
  }
}
