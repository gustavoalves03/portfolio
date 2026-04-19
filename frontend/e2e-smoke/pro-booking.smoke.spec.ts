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

    // Open the stepper. The Angular dev server's HMR occasionally leaves a
    // second MatDialog container in the DOM when we open a dialog on a
    // freshly-hot-reloaded page — so scope every interaction to the latest
    // dialog (`.last()`) rather than the testid directly.
    await page.getByTestId('add-booking-btn').click();
    const dialog = page.locator('mat-dialog-container').last();
    await expect(dialog.getByTestId('booking-stepper')).toBeVisible({ timeout: 10_000 });

    // Step 1 — pick the first care
    const stepCare = dialog.getByTestId('step-care-item');
    await expect(stepCare.first()).toBeVisible({ timeout: 10_000 });
    await stepCare.first().click({ force: true });
    await dialog.getByTestId('step-next-btn').click();

    // Step 2 — datetime
    const target = parseIsoDate(state.suggestedDate);
    await dialog.getByRole('button', { name: /open calendar|ouvrir le calendrier/i }).click();
    await page.getByRole('button', { name: materialDayAriaLabel(target), exact: true }).click();
    await expect(dialog.getByTestId('slot-btn').first()).toBeVisible({ timeout: 10_000 });
    await dialog.getByTestId('slot-btn').first().click();
    await dialog.getByTestId('step-next-btn').click();

    // Step 3 — existing client
    await dialog.getByTestId('client-mode-existing').click();
    await expect(dialog.getByTestId('client-result').first()).toBeVisible({ timeout: 10_000 });
    await dialog.getByTestId('client-result').first().click();
    await dialog.getByTestId('step-confirm-btn').click();

    // Stepper closes on success
    await expect(dialog.getByTestId('booking-stepper')).toBeHidden({ timeout: 10_000 });

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
