import { test, expect } from '@playwright/test';
import { loginAsClient } from '../fixtures/auth.fixture';
import { PUBLIC_SALON } from '../test-data/fixtures';

function json(body: unknown, status = 200) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  };
}

const UPCOMING_BOOKINGS = [
  {
    id: 1001,
    bookingId: 501,
    tenantSlug: PUBLIC_SALON.slug,
    salonName: PUBLIC_SALON.name,
    careName: 'Soin visage',
    carePrice: 5000,
    careDuration: 45,
    appointmentDate: '2099-06-15',
    appointmentTime: '10:00',
    status: 'CONFIRMED',
    createdAt: '2099-06-01T12:00:00',
  },
  {
    id: 1002,
    bookingId: 502,
    tenantSlug: PUBLIC_SALON.slug,
    salonName: PUBLIC_SALON.name,
    careName: 'Massage dos',
    carePrice: 3500,
    careDuration: 30,
    appointmentDate: '2099-06-20',
    appointmentTime: '14:30',
    status: 'CONFIRMED',
    createdAt: '2099-06-02T12:00:00',
  },
];

test.describe('Client booking cancellation', () => {
  test('C5 — client cancels own upcoming booking', async ({ page }) => {
    await loginAsClient(page);

    // Mock upcoming bookings list
    await page.route('**/api/client/me/bookings?tab=upcoming*', r =>
      r.fulfill(json(UPCOMING_BOOKINGS))
    );
    await page.route('**/api/client/me/bookings*', r =>
      r.fulfill(json(UPCOMING_BOOKINGS))
    );
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

    // Track cancel POST
    let cancelCalled = false;
    let cancelledBookingId: number | undefined;
    await page.route(`**/api/salon/${PUBLIC_SALON.slug}/bookings/*/cancel`, async route => {
      if (route.request().method() === 'POST') {
        cancelCalled = true;
        const url = route.request().url();
        const match = url.match(/\/bookings\/(\d+)\/cancel/);
        if (match) cancelledBookingId = Number(match[1]);
        await route.fulfill({ status: 204, body: '' });
      } else {
        await route.continue();
      }
    });

    await page.goto('/bookings');

    // Both cards should be visible
    await expect(page.getByTestId('upcoming-booking-card')).toHaveCount(2);

    // Click cancel on the first booking — opens inline confirm
    await page
      .getByTestId('upcoming-booking-card')
      .first()
      .getByTestId('cancel-booking-btn')
      .click();

    // Confirm button appears
    await expect(page.getByTestId('cancel-confirm-btn')).toBeVisible();
    await page.getByTestId('cancel-confirm-btn').click();

    // Assert the POST was sent to the correct booking id (501)
    await expect.poll(() => cancelCalled, { timeout: 5000 }).toBe(true);
    expect(cancelledBookingId).toBe(501);

    // The cancelled booking should be removed from the list (only 1 remains)
    await expect(page.getByTestId('upcoming-booking-card')).toHaveCount(1);
  });
});
