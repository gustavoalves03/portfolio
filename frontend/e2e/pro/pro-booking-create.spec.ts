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

  test('P2 — new client inline then booking', async ({ page }) => {
    await loginAsPro(page);
    await setupProBookingMocks(page);

    let bookingPayload: Record<string, unknown> | undefined;
    await page.route('**/api/bookings', async route => {
      if (route.request().method() === 'POST') {
        bookingPayload = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ id: 43 }),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/pro/bookings');
    await page.getByTestId('add-booking-btn').click();

    await page.getByTestId('step-care-item').first().click();
    await page.getByTestId('step-next-btn').click();

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

    await page.getByTestId('client-mode-new').click();

    // Fill the create-client form
    const nameInput = page.getByLabel(/nom/i).or(page.getByPlaceholder(/nom/i)).first();
    const phoneInput = page.getByLabel(/téléphone/i).or(page.getByPlaceholder(/téléphone/i)).first();

    await nameInput.fill('Nouvelle Cliente');
    await phoneInput.fill('+33600000000');

    await page.getByTestId('client-create-submit').click();

    // booking POST should fire — the stepper may auto-submit after client creation
    await expect.poll(() => bookingPayload, { timeout: 5000 }).toBeDefined();
    expect(bookingPayload).toHaveProperty('salonClientId', 99);

    await expect(page.getByTestId('booking-stepper')).not.toBeVisible();
  });

  test('P3 — back navigation preserves state', async ({ page }) => {
    await loginAsPro(page);
    await setupProBookingMocks(page);

    await page.goto('/pro/bookings');
    await page.getByTestId('add-booking-btn').click();

    // Step 1 → 2
    await page.getByTestId('step-care-item').first().click();
    await page.getByTestId('step-next-btn').click();

    // Step 2 → 3
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

    // Confirm we're on step 3
    await expect(page.getByTestId('client-mode-existing')).toBeVisible();

    // Back to step 2 — date input should be visible again
    await page.getByTestId('step-back-btn').click();
    await expect(page.getByTestId('booking-date-input')).toBeVisible();

    // Back to step 1 — care items should be visible again
    await page.getByTestId('step-back-btn').click();
    await expect(page.getByTestId('step-care-item').first()).toBeVisible();

    // Re-select a care and proceed forward again
    await page.getByTestId('step-care-item').first().click();
    await page.getByTestId('step-next-btn').click();
    await expect(page.getByTestId('booking-date-input')).toBeVisible();
  });
});
