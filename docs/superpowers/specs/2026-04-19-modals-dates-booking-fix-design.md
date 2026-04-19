# Modal sizing, date format, and salon booking fix — Design

**Date:** 2026-04-19
**Status:** Draft

## Context

Three unrelated issues surfaced while using the public salon booking flow on
`/salon/<slug>`:

1. **Modals open at a fixed size.** Every modal that goes through
   `bottomSheetConfig()` uses a hardcoded `width: '480px'` (desktop) and a
   `height: 75vh` rule in `styles.scss` (mobile). Small modals (confirmations,
   auth prompts) look oversized; content doesn't drive the dimensions.
2. **Datepicker shows `MM/DD/YYYY`.** The two booking datepickers
   (`step-datetime.component.ts`, `booking-dialog.component.ts`) provide the
   native date adapter without a locale, so they default to US formatting even
   though the app's default language is French.
3. **Booking confirmation fails with 409 for every slot.** The public salon
   booking flow returns `409 {"error":"A record with this value already
   exists"}` on confirm. Root cause identified via the backend log:
   `ORA-01400: cannot insert NULL into
   ("TENANT_SOPHIE_MARTIN"."SALON_CLIENTS"."PHONE")`. The source DDL declares
   `PHONE` nullable, but older tenant schemas still have the historical
   `NOT NULL` constraint, and `getOrCreateForUser` passes `null` for phone.

A fourth symptom — the error message `"Ce créneau vient d'être réservé"` is
shown for *every* 409 regardless of actual cause — makes the real problem
invisible. The frontend maps every 409 to `booking.errors.slotTaken` instead of
surfacing the server's real message.

## Goals

- Modals adapt to their content (width and height) within safe bounds, while
  keeping the mobile "bottom sheet" anchoring and slide-up animation.
- Booking datepickers display `DD/MM/YYYY`.
- The public salon booking flow successfully creates a booking for any
  legitimately-available slot on all existing tenant schemas.
- When a booking fails, the UI surfaces the actual server-provided reason.

## Non-goals

- Restructuring `GlobalExceptionHandler` to distinguish `NOT NULL` vs `UNIQUE`
  constraint violations. The `PHONE` fix removes the only known occurrence; any
  remaining improvements belong to their own follow-up.
- Changing the bottom-sheet desktop aesthetic beyond dropping the fixed width.
- Touching any modal that doesn't use `bottomSheetConfig()`.

## Design

### 1. Modal sizing

Update `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts`:

- Remove the hardcoded `width: '480px'`.
- Keep `maxWidth: '100vw'` for safety and add `maxWidth: 'min(480px, 100vw)'`
  as the desktop bound. Material will size the dialog to content up to that
  ceiling.
- Leave `panelClass` and `backdropClass` logic unchanged.

Update `frontend/src/styles.scss` (`.bottom-sheet` mobile block,
`@media max-width: 767px`):

- Replace `height: 75vh; max-height: 75vh` with `max-height: 90vh` only.
- Keep `border-radius`, `padding`, `background`, `box-shadow`,
  `animation: sheet-slide-up`, and the `.sheet-handle` rules intact.
- Keep the fixed-bottom anchoring (`position: fixed; bottom: 0; …`) and the
  full-width rules on mobile — only the height is freed.

Expected outcome: confirmation-style modals collapse to their content height;
the booking stepper expands up to 90vh without scrolling its outer chrome.

### 2. Date format

Create `frontend/src/app/shared/providers/french-date-adapter.ts`:

```ts
export function provideFrenchDateAdapter(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' },
  ]);
}
```

Replace `provideNativeDateAdapter()` with `provideFrenchDateAdapter()` in:

- `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.ts`
- `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts`

The native adapter under `fr-FR` emits `JJ/MM/AAAA` placeholders and parses
`DD/MM/YYYY` input — no further configuration needed.

### 3. `SALON_CLIENTS.PHONE` alignment

Root cause: tenant schemas provisioned before the column was relaxed still
carry `PHONE VARCHAR2(20 CHAR) NOT NULL`, while current `CREATE TABLE`
statements in `TenantSchemaManager` already declare it nullable.

Fix: add idempotent `ALTER` statements to the existing tenant-schema
migration loop so every schema gets aligned on startup.

In `TenantSchemaManager`, inside the Oracle `alterStatements` array (the
`migrateTenantSchema` / migration path that runs at startup for each tenant),
add:

```java
"ALTER TABLE SALON_CLIENTS MODIFY (PHONE VARCHAR2(20 CHAR) NULL)"
```

In the H2 equivalent block:

```java
"ALTER TABLE SALON_CLIENTS ALTER COLUMN PHONE DROP NOT NULL"
```

Both are idempotent on their respective engines (Oracle tolerates re-declaring
a column as NULL when it already is; H2's `DROP NOT NULL` is a no-op when the
column has no constraint). The existing migration loop already swallows the
benign "nothing to do" error codes for ALTERs — confirm the same codes cover
this statement, and extend the skip list if not.

No change to the `CREATE TABLE` statements: they are already correct for new
tenants.

### 4. Booking error surfacing

In `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts`
(`submitBooking`, lines 196–205):

- Replace the 409-special-case with: read `err?.error?.error` when present and
  store it directly as the `bookingError` value. Fall back to
  `'booking.errors.generic'` only when no server message is available.
- Still call `this.loadSlots(this.selectedDate()!)` and
  `this.selectedSlot.set(null)` on 409, so the grid stays in sync when the
  error genuinely is a slot collision.
- The template reads `bookingError()` through the `transloco` pipe today.
  Update it to pass the signal value through as-is when it looks like a plain
  sentence (contains a space or non-identifier character), and through
  `transloco` when it looks like a translation key (`booking.errors.*`). A
  tiny helper `isTranslationKey(s): boolean` keeps the template readable.

The `booking.errors.slotTaken` and `booking.errors.generic` keys stay in
`fr.json` / `en.json` — they're still valid fallbacks.

## Testing

- **Manual**: open `/salon/beaute-du-regard`, pick a care, confirm a booking —
  succeeds.
- **Manual**: same flow on `/salon/sophie-martin` (the reproducer tenant) —
  succeeds after startup migration runs.
- **Manual**: open any small modal (auth, confirmation) — height fits content.
  Open the booking stepper — expands tall, no blank vertical space.
- **Manual**: both booking datepickers display `JJ/MM/AAAA`.
- **Backend unit**: extend `TenantSchemaManagerTests` (or equivalent) if one
  exists, asserting the ALTER statements appear and are idempotent on H2.
- **Frontend unit**: add a test in `booking-dialog.component.spec.ts` (create
  if missing) that a 409 with `{error: "Slot no longer available"}` ends up
  in `bookingError()` as that exact string.

## Risks and open questions

- **Migration ordering on Oracle**: the `ALTER TABLE … MODIFY … NULL` requires
  no rows pending commit. Startup is quiet — no concurrent writers — so this
  is safe, but we should confirm the migration loop runs before any request
  handler picks up.
- **Schema discovery**: the migration loop iterates tenant schemas already;
  the implementation plan should verify we don't need to add `sophie-martin`
  or `beaute-du-regard` to any hand-maintained list.
- **Error message leak**: surfacing raw server errors in the UI is usually
  fine in French/English since they're already sanitized, but the
  implementation plan should sanity-check that no stack-trace-like content
  reaches the user.
