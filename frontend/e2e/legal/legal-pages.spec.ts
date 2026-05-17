import { test, expect } from '@playwright/test';

const PAGES = [
  { path: '/cgu', title: "Conditions Générales d'Utilisation" },
  { path: '/cgv', title: 'Conditions Générales de Vente' },
  { path: '/confidentialite', title: 'Politique de confidentialité' },
  { path: '/mentions-legales', title: 'Mentions légales' },
  { path: '/cookies', title: 'Politique en matière de cookies' },
];

test.describe('Legal pages', () => {
  for (const p of PAGES) {
    test(`renders ${p.path}`, async ({ page }) => {
      await page.goto(p.path);
      await expect(page.getByRole('heading', { level: 1 })).toContainText(p.title);
      await expect(page.locator('.legal-page__updated')).toContainText(/\d{4}/);
      await expect(page.locator('.legal-section').first()).toBeVisible();
    });
  }

  test('footer exposes the 5 legal links and they navigate correctly', async ({ page }) => {
    await page.goto('/cgu');
    // The visitor footer is hidden on mobile (375x667 viewport). Resize to desktop for footer test.
    await page.setViewportSize({ width: 1280, height: 800 });
    // Dismiss the cookie banner so it does not intercept pointer events on footer links.
    const banner = page.locator('.cookie-banner');
    if (await banner.isVisible()) {
      await page.locator('.cookie-banner__btn').click();
      await expect(banner).toHaveCount(0);
    }
    const legalNav = page.locator('.lp-footer__legal-nav');
    await legalNav.scrollIntoViewIfNeeded();
    await expect(legalNav.getByRole('link', { name: /CGU/i })).toBeVisible();
    await legalNav.getByRole('link', { name: /CGU/i }).click();
    await expect(page).toHaveURL(/\/cgu$/);
  });

  test('pre-launch banner shows on /mentions-legales', async ({ page }) => {
    await page.goto('/mentions-legales');
    await expect(page.locator('.legal-prelaunch-banner')).toBeVisible();
  });
});
