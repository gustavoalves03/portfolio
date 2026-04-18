# E2E Booking Tests (Playwright) — Design Spec

**Date:** 2026-04-19
**Status:** Approved (pending user review)
**Scope:** Add Playwright E2E infrastructure + 9 scenarios covering the full booking flow for both `pro` and `client` actors, using HTTP route mocks (no backend required).

## Goal

Provide end-to-end confidence that the two booking creation flows work correctly:

- **Pro flow**: a pro/employee opens `BookingStepperComponent` from `/pro/bookings`, navigates three steps (care → datetime → client), and creates a booking.
- **Client flow**: a client opens `BookingDialogComponent` from `/salon/:slug`, selects date + slot, and confirms.

Tests run without a live backend — `page.route()` intercepts `**/api/**` calls and returns fixture JSON. Tests run on mobile viewport (375×667) by default to match the primary target.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Tool | Playwright |
| Backend | Mocked via `page.route()` |
| Scope | Full — 9 scenarios across both flows |
| Browser | Chromium only (cross-browser coverage out of scope) |
| CI integration | Out of scope — local runs only for now |

## Architecture

### New directory structure

```
frontend/
├── playwright.config.ts
├── e2e/
│   ├── README.md
│   ├── fixtures/
│   │   ├── auth.fixture.ts
│   │   └── mocks.fixture.ts
│   ├── test-data/
│   │   └── fixtures.ts
│   ├── pro/
│   │   └── pro-booking-create.spec.ts
│   └── client/
│       └── client-booking-create.spec.ts
```

### `playwright.config.ts`

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1, // sequential to avoid port conflicts with the dev server
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    viewport: { width: 375, height: 667 }, // iPhone SE
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 667 } } },
  ],
  webServer: {
    command: 'npm start -- --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
```

### `package.json` script additions

```json
"e2e": "playwright test",
"e2e:ui": "playwright test --ui",
"e2e:headed": "playwright test --headed",
"e2e:pro": "playwright test e2e/pro",
"e2e:client": "playwright test e2e/client"
```

### `e2e/fixtures/auth.fixture.ts`

Helpers that simulate an authenticated user by:
1. Injecting `auth_token` into `localStorage` via `page.addInitScript`.
2. Routing `**/api/auth/me` to return a mock user payload (pro, client, or 401 for logged-out).

```typescript
import { Page } from '@playwright/test';

export async function loginAsPro(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('auth_token', 'fake-pro-token');
  });
  await page.route('**/api/auth/me', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      id: 1,
      email: 'pro@test.fr',
      name: 'Pro Test',
      roles: ['PRO'],
      tenantSlug: 'beaute-du-regard',
    }),
  }));
}

export async function loginAsClient(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('auth_token', 'fake-client-token');
  });
  await page.route('**/api/auth/me', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      id: 100,
      email: 'client@test.fr',
      name: 'Client Test',
      roles: ['CLIENT'],
    }),
  }));
}

export async function loggedOut(page: Page): Promise<void> {
  await page.route('**/api/auth/me', route => route.fulfill({ status: 401 }));
}
```

### `e2e/test-data/fixtures.ts`

Static payloads reused across scenarios:

```typescript
export const CARES = [
  { id: 1, name: 'Soin visage', duration: 45, price: 5000, categoryId: 1, status: 'ACTIVE' },
  { id: 2, name: 'Massage dos', duration: 30, price: 3500, categoryId: 1, status: 'ACTIVE' },
];

export const AVAILABLE_SLOTS = [
  { startTime: '10:00', endTime: '10:45' },
  { startTime: '14:30', endTime: '15:15' },
];

export const SALON_CLIENTS = [
  { id: 10, name: 'Marie Dupont', phone: '+33612345678', email: 'marie@test.fr' },
  { id: 11, name: 'Julie Robert', phone: '+33698765432', email: 'julie@test.fr' },
];

export const EMPLOYEES = [
  { id: 5, name: 'Sophie', role: 'EMPLOYEE' },
];
```

### `e2e/fixtures/mocks.fixture.ts`

A single helper per flow that installs all routes for that flow. The caller can override specific routes afterwards (e.g. to make `POST /api/bookings` return 500 for P5).

```typescript
import { Page } from '@playwright/test';
import { CARES, AVAILABLE_SLOTS, SALON_CLIENTS, EMPLOYEES } from '../test-data/fixtures';

export async function setupProBookingMocks(page: Page): Promise<void> {
  await page.route('**/api/cares*', r => r.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(CARES),
  }));
  await page.route('**/api/employees*', r => r.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(EMPLOYEES),
  }));
  await page.route('**/api/pro/opening-hours/available-slots*', r => r.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(AVAILABLE_SLOTS),
  }));
  await page.route('**/api/salon-clients/recent*', r => r.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(SALON_CLIENTS),
  }));
  await page.route('**/api/salon-clients/search*', r => r.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(SALON_CLIENTS),
  }));
  await page.route('**/api/salon-clients', async route => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      await route.fulfill({
        status: 201, contentType: 'application/json',
        body: JSON.stringify({ id: 99, ...body }),
      });
    } else {
      await route.continue();
    }
  });
  await page.route('**/api/bookings/detailed*', r => r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true, empty: true }),
  }));
  await page.route('**/api/bookings', async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201, contentType: 'application/json',
        body: JSON.stringify({ id: 42 }),
      });
    } else {
      await route.continue();
    }
  });
  await page.route('**/api/notifications/unread/count', r => r.fulfill({
    status: 200, contentType: 'application/json', body: '0',
  }));
}

export async function setupClientBookingMocks(page: Page): Promise<void> {
  await page.route('**/api/salon/beaute-du-regard', r => r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({
      slug: 'beaute-du-regard',
      name: 'Beauté du Regard',
      cares: CARES,
    }),
  }));
  await page.route('**/api/salon/beaute-du-regard/available-slots*', r => r.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(AVAILABLE_SLOTS),
  }));
  await page.route('**/api/bookings', async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201, contentType: 'application/json',
        body: JSON.stringify({ id: 77 }),
      });
    } else {
      await route.continue();
    }
  });
}
```

### Test-id additions to production code

To keep selectors stable, we add `data-testid` attributes to templates. This is the only production-code change:

| Component | Element | data-testid |
|---|---|---|
| `ProBookingsComponent` | "+" add booking button | `add-booking-btn` |
| `BookingStepperComponent` | sheet root | `booking-stepper` |
| `BookingStepperComponent` | close (×) | `stepper-close` |
| `BookingStepperComponent` | back arrow | `step-back-btn` |
| `StepCareComponent` | each `.care-card` | `step-care-item` |
| `StepCareComponent` | "next" button | `step-next-btn` |
| `StepDatetimeComponent` | date input | `booking-date-input` |
| `StepDatetimeComponent` | each slot button | `slot-btn` |
| `StepDatetimeComponent` | "next" button | `step-next-btn` |
| `StepClientComponent` | mode card "existing" | `client-mode-existing` |
| `StepClientComponent` | mode card "new" | `client-mode-new` |
| `StepClientComponent` | search input | `client-search-input` |
| `StepClientComponent` | each result row | `client-result` |
| `StepClientComponent` | "confirm" button | `step-confirm-btn` |
| `ClientCreateFormComponent` | submit | `client-create-submit` |
| `BookingDialogComponent` (salon) | date input | `booking-date-input` |
| `BookingDialogComponent` | each slot button | `booking-slot` |
| `BookingDialogComponent` | submit button | `booking-submit` |
| `LoginModalComponent` | submit button | `login-submit` |

## Scenarios

### Pro flow — `pro-booking-create.spec.ts`

- **P1 — Happy path with existing client**: care → datetime → existing client → confirm. Assert `POST /api/bookings` payload + dialog close.
- **P2 — New client inline**: step 3 → "Nouveau client" → fill form → submit → `POST /api/salon-clients` asserted → booking chains.
- **P3 — Back navigation preserves state**: navigate step 1→2→3, press back, verify each previous step still has the selection.
- **P4 — Cancel**: open stepper, click close (×), dialog closes, no `POST /api/bookings` fired.
- **P5 — Server error on booking creation**: complete all steps, mock `POST /api/bookings` → 500, assert error feedback and dialog stays open.

### Client flow — `client-booking-create.spec.ts`

- **C1 — Happy path (logged-in)**: salon page → pick care → dialog opens → select date + slot → submit. Assert `POST /api/bookings`.
- **C2 — Logged-out → login → continues**: salon page without auth → pick care + date + slot → submit → login modal opens → mock login success → booking proceeds.
- **C3 — No slots**: mock available-slots to return `[]` → message "Aucun créneau disponible" visible → submit disabled.
- **C4 — Server error**: like C1 but `POST /api/bookings` → 500. Error feedback, dialog stays open.

## Testing strategy

**Sequential execution** (`workers: 1`) to avoid port conflicts on a single dev server. Each test resets `page.route()` state automatically since it's scoped to the test's page.

**Trace-on-retry** preserved for debugging when a test flakes. Screenshots are captured automatically on failure.

**Assertion style**: Playwright's `expect(locator).toBeVisible()`, `expect(locator).toHaveText()`, `expect.poll(...)` for delayed HTTP state, `expect(page).toHaveURL()` for navigation.

## Out of scope

- **CI / GitHub Actions setup** — local dev-only for now. A follow-up ticket can add `.github/workflows/e2e.yml`.
- **Cross-browser testing** — Chromium only; Firefox / WebKit out of scope.
- **Visual regression / screenshot diff** — no `toMatchSnapshot()` calls.
- **Accessibility audits** — not part of these 9 scenarios.
- **Full-stack smoke test** — deliberately rejected in Q2 (brainstorming).
- **Tests for other booking entry points** — `BookingsDrawerComponent`, `CreateBookingComponent` (orphan), modale salon owner-view — not in the 9.
- **Performance / load** — separate concern.

## Rollout

Single PR. Order of implementation:

1. Install Playwright + config + npm scripts.
2. Add `data-testid` attributes to the components listed in the table above.
3. Create fixtures (`auth.fixture.ts`, `mocks.fixture.ts`, `test-data/fixtures.ts`).
4. Write the 5 pro specs (P1 → P5).
5. Write the 4 client specs (C1 → C4).
6. Run full suite locally, debug, iterate.
7. Write `e2e/README.md` with run instructions.
