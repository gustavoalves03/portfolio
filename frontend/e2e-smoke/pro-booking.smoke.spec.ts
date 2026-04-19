// frontend/e2e-smoke/pro-booking.smoke.spec.ts
//
// SMOKE-2 — Pro crée un booking depuis le stepper (full-stack).

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

  // SMOKE-2 needs an existing salon client row so the "existing client" step
  // has something to pick. Easiest path: book-then-cancel via the client
  // facing API, which persists a salon_clients row as a side effect.
  const { ctx: clientCtx, csrfToken } = await apiContextWithCsrf(state.clientToken);
  const prebook = await postWithCsrf(
    clientCtx,
    csrfToken,
    `/api/salon/${state.salonSlug}/book`,
    {
      careId: state.careId,
      appointmentDate: state.suggestedDate,
      appointmentTime: '09:30',
      employeeId: null,
    }
  );
  expect(prebook.ok(), `pre-book to seed salon_clients should succeed: ${prebook.status()}`).toBe(
    true
  );
  const body = (await prebook.json()) as { bookingId: number };
  const cancel = await postWithCsrf(
    clientCtx,
    csrfToken,
    `/api/salon/${state.salonSlug}/bookings/${body.bookingId}/cancel`
  );
  expect(cancel.ok()).toBe(true);
});

test.describe('SMOKE-2 — pro booking from stepper full-stack', () => {
  test('pro creates a booking for an existing client', async ({ page }) => {
    await injectToken(page, state.proToken);

    await page.goto('/pro/bookings');

    // Open the stepper
    await page.getByTestId('add-booking-btn').click();
    await expect(page.getByTestId('booking-stepper')).toBeVisible({ timeout: 10_000 });

    // Step 1 — pick the first care. Scope to the dialog to avoid any
    // interception from overlapping overlays (e.g. a snackbar from an earlier
    // prebook) and force click — the card is a <div> whose styling can
    // momentarily reflow while the dialog finishes opening.
    const stepCare = page.locator('mat-dialog-container').getByTestId('step-care-item');
    await expect(stepCare.first()).toBeVisible({ timeout: 10_000 });
    await stepCare.first().click({ force: true });
    await page.getByTestId('step-next-btn').click();

    // Step 2 — datetime
    const target = parseIsoDate(state.suggestedDate);
    await page.getByRole('button', { name: /open calendar|ouvrir le calendrier/i }).click();
    await page.getByRole('button', { name: materialDayAriaLabel(target), exact: true }).click();
    await expect(page.getByTestId('slot-btn').first()).toBeVisible({ timeout: 10_000 });
    await page.getByTestId('slot-btn').first().click();
    await page.getByTestId('step-next-btn').click();

    // Step 3 — existing client
    await page.getByTestId('client-mode-existing').click();
    await expect(page.getByTestId('client-result').first()).toBeVisible({ timeout: 10_000 });
    await page.getByTestId('client-result').first().click();
    await page.getByTestId('step-confirm-btn').click();

    // Stepper closes on success
    await expect(page.getByTestId('booking-stepper')).toBeHidden({ timeout: 10_000 });

    // Ground-truth check via the pro API — a new detailed booking should
    // exist in the salon's tenant schema.
    const proCtx = await request.newContext({
      baseURL: BACKEND_BASE_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${state.proToken}` },
    });
    const res = await proCtx.get('/api/bookings/detailed?page=0&size=20');
    expect(res.ok()).toBe(true);
    const body = (await res.json()) as {
      content: Array<{ careId?: number; care?: { id?: number }; status: string }>;
      totalElements: number;
    };
    expect(body.totalElements).toBeGreaterThanOrEqual(1);
  });
});
