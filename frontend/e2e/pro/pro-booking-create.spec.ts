import { test, expect } from '@playwright/test';
import { loginAsPro } from '../fixtures/auth.fixture';
import { setupProBookingMocks } from '../fixtures/mocks.fixture';

test.describe('Pro booking creation', () => {
  test('P1 — happy path with existing client', async ({ page }) => {
    await loginAsPro(page);
    await setupProBookingMocks(page);

    let capturedPayload: Record<string, unknown> | undefined;
    await page.route('**/api/bookings', async route => {
      if (route.request().method() === 'POST') {
        capturedPayload = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ id: 42 }),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/pro/bookings');
    await page.getByTestId('add-booking-btn').click();

    await expect(page.getByTestId('booking-stepper')).toBeVisible();

    // Step 1 — care
    await page.getByTestId('step-care-item').first().click();
    await page.getByTestId('step-next-btn').click();

    // Step 2 — datetime — open Material datepicker and click tomorrow's day cell
    const target = new Date();
    target.setDate(target.getDate() + 1);
    const months = ['January','February','March','April','May','June',
      'July','August','September','October','November','December'];
    const dayAriaLabel = `${months[target.getMonth()]} ${target.getDate()}, ${target.getFullYear()}`;
    await page.getByRole('button', { name: /open calendar|ouvrir le calendrier/i }).click();
    await page.getByRole('button', { name: dayAriaLabel, exact: true }).click();
    await expect(page.getByTestId('slot-btn').first()).toBeVisible();
    await page.getByTestId('slot-btn').first().click();
    await page.getByTestId('step-next-btn').click();

    // Step 3 — existing client
    await page.getByTestId('client-mode-existing').click();
    await expect(page.getByTestId('client-result').first()).toBeVisible();
    await page.getByTestId('client-result').first().click();
    await page.getByTestId('step-confirm-btn').click();

    // Assert POST payload
    await expect.poll(() => capturedPayload, { timeout: 5000 }).toBeDefined();
    expect(capturedPayload).toMatchObject({ careId: 1, status: 'PENDING' });
    expect(capturedPayload).toHaveProperty('salonClientId', 10);

    await expect(page.getByTestId('booking-stepper')).not.toBeVisible();
  });
});
