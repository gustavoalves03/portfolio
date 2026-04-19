// frontend/e2e-smoke/client-booking.smoke.spec.ts
//
// SMOKE-1 — Client prend rendez-vous complet (happy path full-stack).
// No HTTP mocking: real Spring Boot + H2 + Angular dev server.

import { expect, request, test } from '@playwright/test';
import {
  BACKEND_BASE_URL,
  injectToken,
  materialDayAriaLabel,
  parseIsoDate,
  reset,
  seed,
  SmokeSeed,
} from './fixtures/seed.fixture';

let state: SmokeSeed;

test.beforeEach(async () => {
  state = await seed();
  await reset();
});

test.describe('SMOKE-1 — client booking full-stack', () => {
  test('client books a care from the public salon page', async ({ page }) => {
    await injectToken(page, state.clientToken);

    await page.goto(`/salon/${state.salonSlug}`);

    // The salon storefront should render the seeded care card.
    await expect(page.locator('.care-card').first()).toBeVisible();

    // Click the first care's "book" button to open the BookingDialog.
    await page.locator('.care-card').first().getByRole('button').click();

    await expect(page.getByTestId('booking-date-input')).toBeVisible();

    // Pick the date the backend suggested (a future weekday inside opening
    // hours). Using Material's date-picker ensures we go through the same
    // interaction path real users do.
    const target = parseIsoDate(state.suggestedDate);
    await page.locator('mat-datepicker-toggle').first().click();
    await page.getByRole('button', { name: materialDayAriaLabel(target), exact: true }).click();

    // Wait for the backend to return the slot list, then pick one.
    await expect(page.getByTestId('booking-slot').first()).toBeVisible({ timeout: 10_000 });
    await page.getByTestId('booking-slot').first().click();

    await page.getByTestId('booking-submit').click();

    // The dialog closes on success; assert it's gone (avoid flakiness on the
    // toast / confirmation banner whose implementation may evolve).
    await expect(page.getByTestId('booking-date-input')).toBeHidden({ timeout: 10_000 });

    // Ground-truth check: query the backend directly as this client and make
    // sure a single upcoming booking exists for the right care.
    const apiCtx = await request.newContext({
      baseURL: BACKEND_BASE_URL,
      extraHTTPHeaders: {
        Authorization: `Bearer ${state.clientToken}`,
      },
    });
    const res = await apiCtx.get('/api/client/me/bookings?tab=upcoming');
    expect(res.ok()).toBe(true);
    const bookings = (await res.json()) as Array<{
      bookingId: number;
      tenantSlug: string;
      careName: string;
      appointmentDate: string;
      status: string;
    }>;
    expect(bookings.length).toBeGreaterThanOrEqual(1);
    const booking = bookings.find(
      (b) => b.tenantSlug === state.salonSlug && b.appointmentDate === state.suggestedDate
    );
    expect(booking, `should find a booking for ${state.salonSlug} @ ${state.suggestedDate}`)
      .toBeDefined();
    expect(booking!.status).not.toBe('CANCELLED');
  });
});
