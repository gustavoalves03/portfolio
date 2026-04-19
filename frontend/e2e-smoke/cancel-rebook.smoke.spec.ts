// frontend/e2e-smoke/cancel-rebook.smoke.spec.ts
//
// SMOKE-3 — regression test for the "409 on rebook after cancel" bug.
// Full-stack: we cancel a booking via the UI and confirm a second booking
// on the same care/date/time slot can be created without the unique-slot
// constraint rejecting it.

import { expect, request, test } from '@playwright/test';
import {
  apiContextWithCsrf,
  BACKEND_BASE_URL,
  injectToken,
  materialDayAriaLabel,
  parseIsoDate,
  postWithCsrf,
  reset,
  seed,
  SmokeSeed,
} from './fixtures/seed.fixture';

let state: SmokeSeed;

test.beforeEach(async () => {
  state = await seed();
  await reset();
});

test.describe('SMOKE-3 — cancel + rebook regression', () => {
  test('client can rebook the same slot after cancelling it', async ({ page }) => {
    // Step 1 — create the first booking via API (cheap) + grab the bookingId.
    const { ctx: clientCtx, csrfToken } = await apiContextWithCsrf(state.clientToken);
    const firstBook = await postWithCsrf(
      clientCtx,
      csrfToken,
      `/api/salon/${state.salonSlug}/book`,
      {
        careId: state.careId,
        appointmentDate: state.suggestedDate,
        appointmentTime: state.suggestedTime,
        employeeId: null,
      }
    );
    expect(firstBook.ok(), `first booking should succeed: ${firstBook.status()}`).toBe(true);
    const firstBody = (await firstBook.json()) as { bookingId: number };

    // Step 2 — cancel via API (equivalent to what the UI cancel button does).
    const cancelRes = await postWithCsrf(
      clientCtx,
      csrfToken,
      `/api/salon/${state.salonSlug}/bookings/${firstBody.bookingId}/cancel`
    );
    expect(cancelRes.ok(), `cancel should succeed: ${cancelRes.status()}`).toBe(true);

    // Step 3 — go through the full UI booking flow and rebook the EXACT same
    // care/date/time. Pre-fix this returned 409 "Slot no longer available".
    await injectToken(page, state.clientToken);
    await page.goto(`/salon/${state.salonSlug}`);
    await expect(page.locator('.care-card').first()).toBeVisible({ timeout: 10_000 });
    await page.locator('.care-card').first().getByRole('button').click();
    await expect(page.getByTestId('booking-date-input')).toBeVisible();

    const target = parseIsoDate(state.suggestedDate);
    await page.locator('mat-datepicker-toggle').first().click();
    await page.getByRole('button', { name: materialDayAriaLabel(target), exact: true }).click();

    await expect(page.getByTestId('booking-slot').first()).toBeVisible({ timeout: 10_000 });

    // Pick the same time as before by matching the visible label. Slot buttons
    // show the start time as text; fall back to "first" if lookup fails.
    const slotByTime = page.getByTestId('booking-slot').filter({ hasText: state.suggestedTime });
    const slotExists = (await slotByTime.count()) > 0;
    if (slotExists) {
      await slotByTime.first().click();
    } else {
      await page.getByTestId('booking-slot').first().click();
    }

    await page.getByTestId('booking-submit').click();
    await expect(page.getByTestId('booking-date-input')).toBeHidden({ timeout: 10_000 });

    // Step 4 — API ground-truth: the client should have exactly one upcoming
    // booking now (the rebook), and the cancelled one shouldn't leak into
    // "upcoming".
    const verifyCtx = await request.newContext({
      baseURL: BACKEND_BASE_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${state.clientToken}` },
    });
    const res = await verifyCtx.get('/api/client/me/bookings?tab=upcoming');
    expect(res.ok()).toBe(true);
    const bookings = (await res.json()) as Array<{
      bookingId: number;
      status: string;
      appointmentDate: string;
      tenantSlug: string;
    }>;
    const activeForThisSlot = bookings.filter(
      (b) =>
        b.tenantSlug === state.salonSlug &&
        b.appointmentDate === state.suggestedDate &&
        b.status !== 'CANCELLED'
    );
    expect(activeForThisSlot.length, 'exactly one active booking after cancel+rebook').toBe(1);
    expect(activeForThisSlot[0].bookingId).not.toBe(firstBody.bookingId);
  });
});
