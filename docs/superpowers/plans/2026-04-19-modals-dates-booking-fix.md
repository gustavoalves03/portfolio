# Modal sizing, date format, salon booking fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four independent regressions in the public salon booking flow: modals no longer have a content-agnostic size, datepickers show `DD/MM/YYYY`, bookings succeed on legacy tenant schemas, and failures surface the real server reason.

**Architecture:** Four surgical changes isolated by file: `bottom-sheet.config.ts` + `styles.scss` (sizing), a new shared `provideFrenchDateAdapter()` (date format), `TenantSchemaManager` idempotent ALTER (DB schema alignment), and `booking-dialog.component.ts` error mapping (UX).

**Tech Stack:** Angular 20 (standalone components, signals, Material) + Spring Boot 3.5 (Oracle + H2 DDL migration).

**Reference spec:** `docs/superpowers/specs/2026-04-19-modals-dates-booking-fix-design.md`

---

## File Structure

**Files created:**
- `frontend/src/app/shared/providers/french-date-adapter.ts` — shared `EnvironmentProviders` factory combining native date adapter + `fr-FR` locale.

**Files modified:**
- `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts` — drop fixed width, use content-driven bound.
- `frontend/src/styles.scss` — relax mobile bottom-sheet height from 75vh fixed to 90vh max.
- `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.ts` — use `provideFrenchDateAdapter()`.
- `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts` — use `provideFrenchDateAdapter()`, surface server error message.
- `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.html` — conditional transloco vs plain string rendering for `bookingError`.
- `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java` — add idempotent `ALTER TABLE SALON_CLIENTS MODIFY PHONE NULL` to Oracle and H2 alter loops; extend Oracle skip-list to tolerate ORA-01451.

---

## Task 1: Shared French date adapter provider

**Files:**
- Create: `frontend/src/app/shared/providers/french-date-adapter.ts`

- [ ] **Step 1: Create the provider factory**

Create `frontend/src/app/shared/providers/french-date-adapter.ts`:

```ts
import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';

/**
 * Combines Material's native date adapter with the French locale so datepickers
 * display and parse dates as DD/MM/YYYY.
 */
export function provideFrenchDateAdapter(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' },
  ]);
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/shared/providers/french-date-adapter.ts
git commit -m "feat(shared): add provideFrenchDateAdapter for DD/MM/YYYY formatting"
```

---

## Task 2: Apply French date adapter to step-datetime

**Files:**
- Modify: `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.ts:6,20`

- [ ] **Step 1: Swap the import**

In `step-datetime.component.ts`, replace:

```ts
import { provideNativeDateAdapter } from '@angular/material/core';
```

with:

```ts
import { provideFrenchDateAdapter } from '../../../../shared/providers/french-date-adapter';
```

- [ ] **Step 2: Swap the provider**

In the `@Component` decorator, replace:

```ts
providers: [provideNativeDateAdapter()],
```

with:

```ts
providers: [provideFrenchDateAdapter()],
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npx ng build --configuration=development`
Expected: build succeeds, no TS errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.ts
git commit -m "fix(bookings): use French date adapter in step-datetime picker"
```

---

## Task 3: Apply French date adapter to salon booking-dialog

**Files:**
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts:9,38`

- [ ] **Step 1: Swap the import**

In `booking-dialog.component.ts`, replace:

```ts
import { provideNativeDateAdapter } from '@angular/material/core';
```

with:

```ts
import { provideFrenchDateAdapter } from '../../../shared/providers/french-date-adapter';
```

- [ ] **Step 2: Swap the provider**

In the `@Component` decorator, replace:

```ts
providers: [provideNativeDateAdapter()],
```

with:

```ts
providers: [provideFrenchDateAdapter()],
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npx ng build --configuration=development`
Expected: build succeeds.

- [ ] **Step 4: Manual smoke (optional — can wait until end)**

Open `http://localhost:4300/salon/beaute-du-regard`, click "Prendre RDV" on any care. The date field placeholder should show `JJ/MM/AAAA` and selecting a date should format it `DD/MM/YYYY`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts
git commit -m "fix(salon): use French date adapter in booking-dialog picker"
```

---

## Task 4: Content-driven modal sizing — `bottomSheetConfig`

**Files:**
- Modify: `frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts:11-14`

- [ ] **Step 1: Replace the default sizing config**

Replace the body of `bottomSheetConfig` with:

```ts
export function bottomSheetConfig<T = unknown>(
  overrides: MatDialogConfig<T> = {},
): MatDialogConfig<T> {
  return {
    maxWidth: 'min(480px, 100vw)',
    ...overrides,
    panelClass: ['bottom-sheet', ...asArray(overrides.panelClass)],
    backdropClass: ['bottom-sheet-backdrop', ...asArray(overrides.backdropClass)],
  };
}
```

Notes:
- `width: '480px'` is removed — Material will size to content up to `maxWidth`.
- Existing `overrides` continue to win via spread precedence (e.g. the auth modal already passes `width: '480px'` explicitly at the call site in `booking-dialog.component.ts:168` — that still works).

- [ ] **Step 2: Verify build**

Run: `cd frontend && npx ng build --configuration=development`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/shared/uis/sheet-handle/bottom-sheet.config.ts
git commit -m "fix(ui): let bottom-sheet width adapt to content"
```

---

## Task 5: Relax mobile bottom-sheet height — `styles.scss`

**Files:**
- Modify: `frontend/src/styles.scss:161-164`

- [ ] **Step 1: Replace the fixed height rules**

In `styles.scss`, inside `.bottom-sheet { @media (max-width: 767px) { .mat-mdc-dialog-container .mdc-dialog__surface { … } } }`, replace the three lines:

```scss
      border-radius: 16px 16px 0 0;
      height: 75vh;
      max-height: 75vh;
      padding: 0;
```

with:

```scss
      border-radius: 16px 16px 0 0;
      max-height: 90vh;
      padding: 0;
```

Keep all other declarations (`background`, `box-shadow`, `animation`) untouched.

- [ ] **Step 2: Verify build**

Run: `cd frontend && npx ng build --configuration=development`
Expected: build succeeds, no SCSS warnings.

- [ ] **Step 3: Manual smoke (optional — batch with Task 3)**

At mobile width (< 768px), open any small modal (auth, delete confirm). The sheet should hug its content. Open the booking stepper — should expand closer to full height as content grows.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/styles.scss
git commit -m "fix(ui): let bottom-sheet height adapt to content on mobile"
```

---

## Task 6: Backend — relax `SALON_CLIENTS.PHONE` on legacy tenant schemas

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java:267-279,305-309,471-483`

- [ ] **Step 1: Add Oracle ALTER to the idempotent alter list**

In the Oracle migration method (the `alterStatements` array around line 267), add one entry at the end of the array:

```java
// Align legacy tenant schemas with the nullable PHONE column declared in CREATE TABLE.
"ALTER TABLE SALON_CLIENTS MODIFY (PHONE VARCHAR2(20 CHAR) NULL)"
```

Full updated array (append after `"ALTER TABLE CARE_BOOKINGS ADD (SALON_CLIENT_ID NUMBER(19))"`):

```java
String[] alterStatements = {
        "ALTER TABLE OPENING_HOURS ADD (EMPLOYEE_ID NUMBER(19))",
        "ALTER TABLE BLOCKED_SLOTS ADD (EMPLOYEE_ID NUMBER(19))",
        "ALTER TABLE CARE_BOOKINGS ADD (EMPLOYEE_ID NUMBER(19))",
        "ALTER TABLE CLIENT_PROFILES ADD (UPDATED_AT TIMESTAMP)",
        "ALTER TABLE CLIENT_PROFILES ADD (UPDATED_BY NUMBER(19))",
        "ALTER TABLE VISIT_RECORDS ADD (UPDATED_AT TIMESTAMP)",
        "ALTER TABLE VISIT_RECORDS ADD (UPDATED_BY NUMBER(19))",
        "ALTER TABLE VISIT_PHOTOS ADD (UPLOADED_BY NUMBER(19))",
        "ALTER TABLE CLIENT_REMINDERS ADD (CREATED_BY NUMBER(19))",
        "ALTER TABLE CARE_BOOKINGS ADD (SALON_CLIENT_ID NUMBER(19))",
        // Align legacy tenant schemas with the nullable PHONE column declared in CREATE TABLE.
        "ALTER TABLE SALON_CLIENTS MODIFY (PHONE VARCHAR2(20 CHAR) NULL)"
};
```

- [ ] **Step 2: Extend Oracle skip-list to tolerate "already nullable"**

In the same Oracle method, the loop around line 300 currently skips only error `1430`. Extend it to also skip error `1451` (`ORA-01451: column to be modified to NULL cannot be modified to NULL`), which fires when Oracle rejects a redundant re-assertion:

```java
for (String alter : alterStatements) {
    try {
        stmt.execute(alter);
        logger.info("Applied ALTER in {}", schemaName);
    } catch (SQLException e) {
        int code = e.getErrorCode();
        if (code == 1430 || code == 1451) {
            logger.debug("ALTER is a no-op in {} (error {}), skipping", schemaName, code);
        } else {
            logger.warn("ALTER failed in {} (error {}): {}", schemaName, code, e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Add H2 ALTER to the H2 alter list**

In the H2 migration method (the `alterStatements` array around line 471), add at the end:

```java
// Align legacy H2 tenant schemas with the nullable PHONE column declared in CREATE TABLE.
"ALTER TABLE SALON_CLIENTS ALTER COLUMN PHONE DROP NOT NULL"
```

Full updated array:

```java
String[] alterStatements = {
        "ALTER TABLE OPENING_HOURS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
        "ALTER TABLE BLOCKED_SLOTS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
        "ALTER TABLE CARE_BOOKINGS ADD COLUMN IF NOT EXISTS EMPLOYEE_ID BIGINT",
        "ALTER TABLE CLIENT_PROFILES ADD COLUMN IF NOT EXISTS UPDATED_AT TIMESTAMP",
        "ALTER TABLE CLIENT_PROFILES ADD COLUMN IF NOT EXISTS UPDATED_BY BIGINT",
        "ALTER TABLE VISIT_RECORDS ADD COLUMN IF NOT EXISTS UPDATED_AT TIMESTAMP",
        "ALTER TABLE VISIT_RECORDS ADD COLUMN IF NOT EXISTS UPDATED_BY BIGINT",
        "ALTER TABLE VISIT_PHOTOS ADD COLUMN IF NOT EXISTS UPLOADED_BY BIGINT",
        "ALTER TABLE CLIENT_REMINDERS ADD COLUMN IF NOT EXISTS CREATED_BY BIGINT",
        "ALTER TABLE CARE_BOOKINGS ADD COLUMN IF NOT EXISTS SALON_CLIENT_ID BIGINT",
        // Align legacy H2 tenant schemas with the nullable PHONE column declared in CREATE TABLE.
        "ALTER TABLE SALON_CLIENTS ALTER COLUMN PHONE DROP NOT NULL"
};
```

Note: The H2 method (line 494 loop) executes ALTERs without a try/catch, so any ALTER that fails will abort migration. `ALTER … DROP NOT NULL` on an already-nullable column is silently no-op in H2, so this is safe.

- [ ] **Step 4: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Run backend tests**

Run: `cd backend && mvn -q test`
Expected: all tests pass. The H2-backed tests will exercise the new ALTER at schema creation; it should be a no-op since the fresh H2 schema already has PHONE as nullable.

- [ ] **Step 6: Start the app and confirm migration log**

Run: `cd backend && mvn spring-boot:run`

In a separate terminal, watch the startup log. For each existing tenant schema (including `TENANT_SOPHIE_MARTIN` and `TENANT_BEAUTE_DU_REGARD`), you should see either:
- `Applied ALTER in TENANT_SOPHIE_MARTIN` (the real fix firing), OR
- `ALTER is a no-op in <schema> (error 1451), skipping` (already nullable — benign).

Stop the app after confirming (Ctrl+C).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java
git commit -m "fix(tenancy): relax SALON_CLIENTS.PHONE NOT NULL on legacy tenant schemas

Older tenant schemas were provisioned before the PHONE column was declared
nullable in CREATE TABLE. That left a latent NOT NULL that blocked the
auto-creation of a SalonClient during public salon bookings (the service
passes phone=null for users registering via a salon booking flow).

Adds an idempotent ALTER to the tenant-schema migration loop for both
Oracle and H2. Oracle error 1451 is added to the skip-list so the
statement is a clean no-op when the column is already nullable."
```

---

## Task 7: Frontend — surface real server error on booking failure

**Files:**
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts:196-206`
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.html:87-89`

- [ ] **Step 1: Add translation-key detector helper**

In `booking-dialog.component.ts`, inside the `BookingDialogComponent` class (near the other helpers), add:

```ts
/**
 * Heuristic: our i18n keys look like `booking.errors.xxx` — lowercase, dotted,
 * no spaces. A plain server-provided sentence contains spaces or punctuation.
 */
isTranslationKey(value: string): boolean {
  return /^[a-zA-Z][a-zA-Z0-9._]*$/.test(value);
}
```

- [ ] **Step 2: Replace the error branch in `submitBooking`**

Replace lines 196–205 (the `error:` handler inside `submitBooking`) with:

```ts
      error: (err) => {
        this.submitting.set(false);
        const serverMsg = err?.error?.error;
        if (err.status === 409) {
          // Keep the grid in sync: a real slot collision invalidates our cache.
          this.loadSlots(this.selectedDate()!);
          this.selectedSlot.set(null);
        }
        if (typeof serverMsg === 'string' && serverMsg.length > 0) {
          this.bookingError.set(serverMsg);
        } else {
          this.bookingError.set('booking.errors.generic');
        }
      },
```

- [ ] **Step 3: Update the template to render both keys and plain strings**

In `booking-dialog.component.html`, replace line 88:

```html
      <p class="booking-error">{{ bookingError()! | transloco }}</p>
```

with:

```html
      <p class="booking-error">
        @if (isTranslationKey(bookingError()!)) {
          {{ bookingError()! | transloco }}
        } @else {
          {{ bookingError() }}
        }
      </p>
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npx ng build --configuration=development`
Expected: build succeeds.

- [ ] **Step 5: Manual smoke**

With the backend running (Task 6 step 6 already started it, or restart it), open `/salon/beaute-du-regard` or `/salon/sophie-martin`, pick any care, pick a date and a slot, and confirm:

- **Expected:** booking success view appears. The bug from the spec ("A record with this value already exists" message) is gone.
- Bonus check: if you manually force a 409 (e.g. book the same slot twice in a row), the error shown is the server's own message (`Slot no longer available` or `Ce créneau vient d'être réservé` from the prior i18n key), not silently rewritten.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts \
        frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.html
git commit -m "fix(salon): surface server error message on booking failure

Previously every 409 was hardcoded to booking.errors.slotTaken, which
masked the real reason when a different conflict surfaced (e.g. the
recent ORA-01400 NOT NULL on SALON_CLIENTS.PHONE that the backend
reported as 'A record with this value already exists').

The component now reads err.error.error and displays it as-is when it's
a plain sentence, falling back to booking.errors.generic only when the
server provided no message. Translation keys are still rendered through
Transloco so existing keys (slotTaken) keep working if the backend
switches to that form."
```

---

## Task 8: Final verification pass

- [ ] **Step 1: Full frontend build + tests**

Run: `cd frontend && npm run build && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: build succeeds; all tests pass.

- [ ] **Step 2: Full backend build + tests**

Run: `cd backend && mvn -q verify`
Expected: BUILD SUCCESS.

- [ ] **Step 3: End-to-end manual smoke**

With backend running (`mvn spring-boot:run`) and frontend running (`npm start` or the existing Docker container on port 4300):

1. Open `http://localhost:4300/salon/beaute-du-regard` (and/or `sophie-martin`).
2. Click "Prendre RDV" on a care.
3. Confirm: the modal fits its content — no empty bottom half.
4. Confirm: date picker shows `JJ/MM/AAAA`.
5. Pick a date (future, open day), pick a slot, confirm.
6. Expected: success view appears.
7. Try again immediately with the exact same slot (as a second user if needed) and confirm the 409 now shows a readable message.

- [ ] **Step 4: If anything fails, document and stop**

If any step 3 check fails, stop, copy the failing output, and report back before continuing. Do not improvise fixes — they belong in a new plan or an amendment.

---

## Self-review checklist (performed by the plan author)

- **Spec coverage:**
  - Goal 1 (modal sizing) → Tasks 4, 5. ✓
  - Goal 2 (date format) → Tasks 1, 2, 3. ✓
  - Goal 3 (booking succeeds) → Task 6. ✓
  - Goal 4 (real error surfacing) → Task 7. ✓
  - Final verification → Task 8. ✓
- **Placeholder scan:** no TBD / TODO / "similar to". Each code step has explicit code or an explicit command.
- **Type consistency:** `provideFrenchDateAdapter()` signature matches across Tasks 1–3. Helper `isTranslationKey()` used in Task 7 is introduced in that same task. `bookingError` remains a `WritableSignal<string | null>`.
- **Commit cadence:** eight commits, each scoped to one independent change, each passing its own build.
