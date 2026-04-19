# Playwright E2E Tests

End-to-end tests for the booking flows (pro and client). Uses Playwright with mocked HTTP responses — no backend required.

## Run

From `frontend/`:

- `npm run e2e` — run all tests
- `npm run e2e:ui` — interactive UI (debug)
- `npm run e2e:headed` — run with visible browser
- `npm run e2e:pro` — pro flow only (P1–P5)
- `npm run e2e:client` — client flow only (C1–C4)

The dev server starts automatically via `playwright.config.ts` → `webServer`. If port 4200 is already in use locally, the existing server is reused.

## Structure

```
e2e/
├── fixtures/
│   ├── auth.fixture.ts      — loginAsPro / loginAsClient / loggedOut
│   └── mocks.fixture.ts     — setupProBookingMocks / setupClientBookingMocks
├── test-data/
│   └── fixtures.ts          — CARES, AVAILABLE_SLOTS, SALON_CLIENTS, EMPLOYEES, PUBLIC_SALON
├── pro/
│   └── pro-booking-create.spec.ts     — P1-P5
└── client/
    └── client-booking-create.spec.ts  — C1-C4
```

## Scenarios

**Pro** (`/pro/bookings` → add booking stepper):
- **P1** — happy path with existing client
- **P2** — new client inline, then booking
- **P3** — back navigation (relaxed: state is not preserved in current impl)
- **P4** — cancel closes dialog (no booking posted)
- **P5** — server 500 keeps dialog open

**Client** (`/salon/:slug` → booking dialog):
- **C1** — happy path logged-in client
- **C2** — logged-out triggers login modal on submit
- **C3** — no available slots, submit blocked
- **C4** — server 500 keeps dialog open

## Adding a test

1. Pick the folder matching the flow (`e2e/pro` or `e2e/client`).
2. Import `loginAsPro`/`loginAsClient` + the appropriate mocks setup.
3. Override specific routes for scenario-specific behaviour.
4. Use `data-testid` selectors (see the design spec for the full list).

## Tips

- **Material datepicker** is not drivable via `fill()`. Open the calendar toggle (`mat-datepicker-toggle`) and click the day cell by English aria-label (`April 20, 2026`). This is intentional — Playwright's `getByRole('button', { name: dayAriaLabel })` is the stable approach.
- **Selectors** rely on `data-testid` attributes. If a test breaks, check whether the component was refactored and dropped the testid.
- **Mocks** are scoped to each test automatically — no cleanup needed between tests.
- **Sequential workers**: `workers: 1` in config to avoid dev-server port contention. Don't change unless you spin up multiple servers.

## Viewport

Tests default to **iPhone SE (375×667)** to match the mobile-first product focus. Override via `page.setViewportSize()` if a desktop-specific scenario is ever needed.
