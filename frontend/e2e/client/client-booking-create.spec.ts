import { test, expect } from '@playwright/test';
import { loginAsClient, loggedOut } from '../fixtures/auth.fixture';
import { setupClientBookingMocks } from '../fixtures/mocks.fixture';
import { PUBLIC_SALON } from '../test-data/fixtures';

test.describe('Client booking creation', () => {
  test('C1 — happy path logged-in client', async ({ page }) => {
    await loginAsClient(page);
    await setupClientBookingMocks(page);

    let bookingPayload: Record<string, unknown> | undefined;
    await page.route(`**/api/salon/${PUBLIC_SALON.slug}/book`, async route => {
      if (route.request().method() === 'POST') {
        bookingPayload = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ id: 77 }),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto(`/salon/${PUBLIC_SALON.slug}`);

    // Click the book button of the first care to open BookingDialog
    await page.locator('.care-card').first().getByRole('button').click();

    // The dialog should open — wait for its date input
    await expect(page.getByTestId('booking-date-input')).toBeVisible();

    // Calculate target date (tomorrow) and build Material's aria-label
    const target = new Date();
    target.setDate(target.getDate() + 1);
    const months = [
      'January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December',
    ];
    const dayAriaLabel = `${months[target.getMonth()]} ${target.getDate()}, ${target.getFullYear()}`;

    // Open the datepicker and click tomorrow's day cell
    await page.locator('mat-datepicker-toggle').first().click();
    await page.getByRole('button', { name: dayAriaLabel, exact: true }).click();

    // Pick the first slot
    await expect(page.getByTestId('booking-slot').first()).toBeVisible();
    await page.getByTestId('booking-slot').first().click();

    // Submit the booking
    await page.getByTestId('booking-submit').click();

    await expect.poll(() => bookingPayload, { timeout: 5000 }).toBeDefined();
    expect(bookingPayload).toMatchObject({ careId: 1 });
  });

  test('C2 — logged-out triggers login modal on submit', async ({ page }) => {
    await loggedOut(page);
    await setupClientBookingMocks(page);

    await page.goto(`/salon/${PUBLIC_SALON.slug}`);
    await page.locator('.care-card').first().getByRole('button').click();

    await expect(page.getByTestId('booking-date-input')).toBeVisible();

    const target = new Date();
    target.setDate(target.getDate() + 1);
    const months = [
      'January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December',
    ];
    const dayAriaLabel = `${months[target.getMonth()]} ${target.getDate()}, ${target.getFullYear()}`;
    await page.locator('mat-datepicker-toggle').first().click();
    await page.getByRole('button', { name: dayAriaLabel, exact: true }).click();

    await expect(page.getByTestId('booking-slot').first()).toBeVisible();
    await page.getByTestId('booking-slot').first().click();
    await page.getByTestId('booking-submit').click();

    // Auth modal should appear with the login submit button visible
    await expect(page.getByTestId('login-submit')).toBeVisible();
  });
});
