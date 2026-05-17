import { test, expect } from '@playwright/test';

test.describe('Cookie banner', () => {
  test.beforeEach(async ({ page }) => {
    await page.context().clearCookies();
    // Navigate to the app first so localStorage is accessible, then clear the key.
    // Using page.evaluate (not addInitScript) so the cleanup only runs once and does NOT
    // re-execute on subsequent reloads — which would break the persistence test.
    await page.goto('/cgu');
    await page.evaluate(() => {
      try { localStorage.removeItem('lp_cookie_banner_v1'); } catch {}
    });
  });

  test('banner is visible on first visit', async ({ page }) => {
    await page.goto('/cgu');
    await expect(page.locator('.cookie-banner')).toBeVisible();
  });

  test('clicking the dismiss button hides the banner', async ({ page }) => {
    await page.goto('/cgu');
    await page.locator('.cookie-banner__btn').click();
    await expect(page.locator('.cookie-banner')).toHaveCount(0);
  });

  test('dismissal persists across reloads', async ({ page }) => {
    await page.goto('/cgu');
    await page.locator('.cookie-banner__btn').click();
    await expect(page.locator('.cookie-banner')).toHaveCount(0);
    await page.reload();
    await expect(page.locator('.cookie-banner')).toHaveCount(0);
  });

  test('the "learn more" link navigates to /cookies', async ({ page }) => {
    await page.goto('/cgu');
    await page.locator('.cookie-banner__link').click();
    await expect(page).toHaveURL(/\/cookies$/);
  });
});
