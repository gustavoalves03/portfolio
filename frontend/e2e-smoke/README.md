# Playwright Smoke Tests (full-stack)

End-to-end **smoke** tests that run against the real Spring Boot backend and a real Angular dev server — **no HTTP mocking**. Data is created via a profile-gated `/api/test/seed` endpoint and the in-memory H2 database that the `smoke-test` Spring profile boots up.

These complement the mocked suite under `../e2e` — don't replace it. Mocked tests are fast and cover many UI scenarios; smoke tests are slower but prove the whole stack (controllers, security, multi-tenancy, persistence, CSRF) wires together for the happy paths that matter most.

## When to run

- **Before every push that touches the booking layer or security.** Mocked tests won't catch JWT / CSRF / tenant-context regressions.
- **After merging backend changes** (new migration, new endpoint, new filter).
- **Before cutting a release.**

## Run

From `frontend/`:

```bash
npm run e2e:smoke                                          # all 3 scenarios
npx playwright test --config playwright.smoke.config.ts    # same thing, no alias
```

The runner boots two servers automatically (sequential, `workers: 1`):

1. Backend: `./mvnw spring-boot:test-run -Dspring-boot.run.profiles=smoke-test` on port 8080. Uses the `smoke-test` profile (`backend/src/test/resources/application-smoke-test.properties`) which points at an in-memory H2 database (`jdbc:h2:mem:smoketestdb;MODE=Oracle`) and no real SMTP/OAuth2.
2. Frontend: `npm start -- --port 4200`.

Expect a **60–120 s startup cost** on first run (Maven + Angular cold build). Subsequent runs reuse both servers thanks to `reuseExistingServer`.

## Requirements

- Backend builds locally (`cd backend && ./mvnw compile` works).
- Java 21.
- Ports 8080 and 4200 free.
- No production Oracle required — the `smoke-test` profile uses H2 only.

## Scenarios

All three use the same seed (one tenant `beaute-du-regard`, one PRO user, one client user `marie-smoke@test.com`, one care "Soin visage", opening hours Mon–Sat 9h–19h):

- **SMOKE-1** — `client-booking.smoke.spec.ts` — a logged-in client opens the public salon page, books the first care on the suggested date + 10:00, and we assert via `GET /api/client/me/bookings` that the booking was persisted.
- **SMOKE-2** — `pro-booking.smoke.spec.ts` — a logged-in PRO opens the stepper, books for an existing client (pre-seeded via a cancelled booking that creates the salon_clients row), and we assert via `GET /api/bookings/detailed` that the booking reached the tenant schema.
- **SMOKE-3** — `cancel-rebook.smoke.spec.ts` — regression test for the 409 bug on rebooking a cancelled slot. Creates a booking via API, cancels it, then rebooks the exact same care/date/time via the UI, asserting exactly one active booking remains.

## Data isolation between tests

- `beforeEach` calls `POST /api/test/seed` (idempotent) to make sure users / tenant / care exist, then `POST /api/test/reset` to wipe bookings from the tenant schema and the client-history mirror. Users and the care catalog survive reset — fast restart.
- One worker (`workers: 1`) so the shared H2 + tenant schema doesn't see concurrent mutations.

## Structure

```
e2e-smoke/
├── README.md                          — this file
├── fixtures/
│   └── seed.fixture.ts                — seed(), reset(), injectToken(), CSRF helpers
├── client-booking.smoke.spec.ts       — SMOKE-1
├── pro-booking.smoke.spec.ts          — SMOKE-2
└── cancel-rebook.smoke.spec.ts        — SMOKE-3
```

## Safety

The seed endpoint is registered by `SmokeTestSeedController` under `@Profile("smoke-test")`. It does NOT exist outside that profile — dev, staging and prod never see it. SecurityConfig whitelists `/api/test/**` (permitAll + CSRF-exempt), but that whitelist is inert whenever the controller isn't on the classpath.

## Troubleshooting

- **"seed endpoint failed: 403"** — backend isn't running with `smoke-test` profile. Check the Playwright webServer stdout / rerun with `--reporter=list`.
- **"Timed out waiting from config.webServer"** — usually port 4200 or 8080 is held by another process. Kill with `lsof -nP -iTCP:4200 -sTCP:LISTEN` / `:8080` and retry.
- **Care card not visible** — the seed didn't land in the tenant schema. Make sure the seed controller is **not** `@Transactional` (see the code comment); Hibernate resolves the tenant identifier at transaction open time.
- **Datepicker day not clickable** — the `suggestedDate` may fall on a Sunday (closed). The seed returns a Sat-or-weekday to avoid this.
