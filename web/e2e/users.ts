import path from 'node:path';
import { expect, type Page } from '@playwright/test';

/** Test users from infra/keycloak/itsm-realm.json (password = username). */
export const USERS = ['customer1', 'customer2', 'agent1', 'manager1'] as const;
export type TestUser = (typeof USERS)[number];

export function storageStatePath(user: TestUser): string {
  return path.join(import.meta.dirname, '.auth', `${user}.json`);
}

/** Fills the Keycloak login form the app redirects to and waits until the app is back. */
export async function loginWithKeycloak(page: Page, user: TestUser): Promise<void> {
  await expect(page).toHaveURL(/\/realms\/itsm\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(user);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('button', { name: 'Çıkış' })).toBeVisible();
}
