import type { Page } from '@playwright/test';
import { expect, test, uniqueTitle } from './fixtures.ts';

/** Requests the laptop (needs approval) and returns the request id; leaves the page on the request. */
async function requestLaptop(customer: Page, reason: string): Promise<number> {
  await customer.goto('/catalog');
  await customer.getByRole('link', { name: 'Yeni dizüstü bilgisayar' }).click();
  const form = customer.getByRole('form', { name: 'Hizmet talebi' });
  await form.getByLabel('Model *').selectOption({ label: 'Standart (14 inç)' });
  await form.getByLabel('Gerekçe *').fill(reason);
  await form.getByRole('button', { name: 'İste' }).click();
  await expect(customer.getByRole('heading', { level: 1 })).toContainText('Yeni dizüstü bilgisayar');
  await expect(customer.getByText('Onay bekliyor').first()).toBeVisible();
  return Number(new URL(customer.url()).pathname.split('/').pop());
}

test('a manager approves a requested laptop and the customer follows it to its ticket', async ({ as }) => {
  const reason = uniqueTitle('dizüstü gerekçe');
  const customer = await as('customer1');
  const id = await requestLaptop(customer, reason);

  const manager = await as('manager1');
  await manager.goto('/approvals');
  const decision = manager.getByRole('form', { name: `Karar #${id}` });
  await expect(manager.getByRole('listitem').filter({ hasText: reason })).toBeVisible();
  await decision.getByLabel('Not (reddederken zorunlu)').fill('Bütçe uygun');
  await decision.getByRole('button', { name: 'Onayla' }).click();
  await expect(decision).toHaveCount(0);

  await customer.reload();
  await expect(customer.getByText('Karşılanıyor')).toBeVisible();
  await expect(customer.getByText('Bütçe uygun')).toBeVisible();
  await customer.getByRole('link', { name: /numaralı talepte/ }).click();
  await expect(customer.getByRole('heading', { level: 1 })).toContainText('Yeni dizüstü bilgisayar');
  await expect(customer.getByText(`Hizmet talebi #${id}`)).toBeVisible();
});

test('a rejection needs a reason, which the customer then reads', async ({ as }) => {
  const customer = await as('customer1');
  const id = await requestLaptop(customer, uniqueTitle('red gerekçe'));

  const manager = await as('manager1');
  await manager.goto(`/requests/${id}`);
  const decision = manager.getByRole('form', { name: `Karar #${id}` });
  await expect(decision.getByRole('button', { name: 'Reddet' })).toBeDisabled();
  await decision.getByLabel('Not (reddederken zorunlu)').fill('Bu çeyrekte bütçe yok');
  await decision.getByRole('button', { name: 'Reddet' }).click();
  await expect(manager.getByText('Reddedildi').first()).toBeVisible();

  await customer.reload();
  await expect(customer.getByText('Bu çeyrekte bütçe yok')).toBeVisible();
  await expect(customer.getByRole('link', { name: /numaralı talepte/ })).toHaveCount(0);
});

test('a request without approval becomes a ticket at once; customers cannot decide', async ({ as }) => {
  const customer = await as('customer1');
  await customer.goto('/catalog');
  await customer.getByRole('link', { name: 'VPN erişimi' }).click();
  await customer.getByRole('form', { name: 'Hizmet talebi' }).getByLabel('Cihaz *').selectOption({ label: 'Telefon' });
  await customer.getByRole('button', { name: 'İste' }).click();
  await expect(customer.getByText('Karşılanıyor')).toBeVisible();
  await expect(customer.getByRole('link', { name: /numaralı talepte/ })).toBeVisible();
  await expect(customer.getByRole('form', { name: /Karar/ })).toHaveCount(0);

  await customer.goto('/approvals');
  await expect(customer.getByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeVisible();
});
