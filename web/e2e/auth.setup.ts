import { test as setup } from '@playwright/test';
import { loginWithKeycloak, storageStatePath, USERS } from './users.ts';

// Signs every test user in once; the saved Keycloak session cookie skips the login form in the tests.
for (const user of USERS) {
  setup(`sign in ${user}`, async ({ page }) => {
    await page.goto('/');
    await loginWithKeycloak(page, user);
    await page.context().storageState({ path: storageStatePath(user) });
  });
}
