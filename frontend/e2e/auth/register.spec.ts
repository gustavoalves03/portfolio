import { test, expect } from '@playwright/test';
import { loggedOut } from '../fixtures/auth.fixture';

function json(body: unknown, status = 200) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  };
}

const MOCK_USER = {
  id: 777,
  email: 'newclient@test.fr',
  name: 'New Client',
  role: 'USER',
  provider: 'LOCAL',
};

test.describe('Client registration', () => {
  test('R1 — successful registration redirects to home', async ({ page }) => {
    await loggedOut(page);

    let capturedPayload: Record<string, unknown> | undefined;
    await page.route('**/api/auth/register/client', async route => {
      if (route.request().method() === 'POST') {
        capturedPayload = route.request().postDataJSON();
        // After registration, subsequent /api/auth/me calls should succeed.
        await route.fulfill(
          json({
            accessToken: 'fake-new-client-token',
            tokenType: 'Bearer',
            user: MOCK_USER,
          })
        );
      } else {
        await route.continue();
      }
    });

    // Minimal mocks to keep the shell happy
    await page.route('**/api/notifications/unread/count', r => r.fulfill(json(0)));
    await page.route('**/api/notifications*', r =>
      r.fulfill(
        json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
          first: true,
          last: true,
          empty: true,
        })
      )
    );

    await page.goto('/register');

    await expect(page.getByTestId('register-form')).toBeVisible();

    // Fill form
    await page.getByTestId('register-name').fill('New Client');
    await page.getByTestId('register-email').fill('newclient@test.fr');
    await page.getByTestId('register-password').fill('strongpassword123');

    // Mat checkbox: click the inner input via the host's testid area
    const consent = page.getByTestId('register-consent');
    await consent.locator('input[type="checkbox"]').check({ force: true });

    // Submit
    await page.getByTestId('register-submit').click();

    // Assert the POST fired with the right payload
    await expect.poll(() => capturedPayload, { timeout: 5000 }).toBeDefined();
    expect(capturedPayload).toMatchObject({
      name: 'New Client',
      email: 'newclient@test.fr',
      password: 'strongpassword123',
      consent: true,
    });

    // AuthService.navigateByRole() sends USER role to '/'
    await expect.poll(() => page.url(), { timeout: 5000 }).toMatch(/\/$|\/$/);
    // Form should no longer be visible (we left the register page)
    await expect(page.getByTestId('register-form')).toHaveCount(0);
  });

  test('R1b — duplicate email shows conflict error', async ({ page }) => {
    await loggedOut(page);

    let conflictFired = false;
    await page.route('**/api/auth/register/client', async route => {
      if (route.request().method() === 'POST') {
        conflictFired = true;
        await route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Email already in use' }),
        });
      } else {
        await route.continue();
      }
    });

    await page.route('**/api/notifications/unread/count', r => r.fulfill(json(0)));
    await page.route('**/api/notifications*', r =>
      r.fulfill(
        json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
          first: true,
          last: true,
          empty: true,
        })
      )
    );

    await page.goto('/register');

    await expect(page.getByTestId('register-form')).toBeVisible();

    await page.getByTestId('register-name').fill('Existing User');
    await page.getByTestId('register-email').fill('existing@test.fr');
    await page.getByTestId('register-password').fill('strongpassword123');

    const consent = page.getByTestId('register-consent');
    await consent.locator('input[type="checkbox"]').check({ force: true });

    await page.getByTestId('register-submit').click();

    // Conflict endpoint fired
    await expect.poll(() => conflictFired, { timeout: 5000 }).toBe(true);

    // Form stays on screen (no redirect) — user can correct the email and retry.
    await expect(page.getByTestId('register-form')).toBeVisible();
    await expect(page.url()).toMatch(/\/register/);

    // After submit + 409 response, submit must be re-enabled and conflict error visible.
    await expect(page.getByTestId('register-submit')).toBeEnabled();
    await expect(page.getByTestId('register-email-conflict')).toBeVisible();
  });
});
