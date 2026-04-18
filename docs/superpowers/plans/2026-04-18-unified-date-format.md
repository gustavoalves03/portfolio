# Unified Date Format Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a shared date-formatting utility (`date-format.ts`) plus two standalone pipes (`AppDatePipe`, `AppDateTimePipe`), then refactor every ad-hoc date formatting call to use them — so every full-date renders as `dd/MM/yyyy` and every timestamp as `dd/MM/yyyy HH:mm` across the entire app. Weekday and relative labels are preserved.

**Architecture:** Pure-TypeScript utility in `core/utils/`, two standalone Angular pipes in `shared/pipes/`. Templates use `| appDate` or `| appDateTime`; components call the utility functions directly. The existing duplicated `toYMD`/`addDays` helpers are consolidated into the utility.

**Tech Stack:** Angular 20 (standalone, zoneless), Jasmine/Karma, TypeScript. No i18n impact — the format is locale-agnostic by design.

**Spec:** `docs/superpowers/specs/2026-04-18-unified-date-format-design.md`

---

## File Structure

**New files (5):**
- `frontend/src/app/core/utils/date-format.ts` — pure utility (5 exported functions)
- `frontend/src/app/core/utils/date-format.spec.ts` — unit tests for utility (8 cases)
- `frontend/src/app/shared/pipes/app-date.pipe.ts` — `| appDate`
- `frontend/src/app/shared/pipes/app-datetime.pipe.ts` — `| appDateTime`
- `frontend/src/app/shared/pipes/app-date.pipe.spec.ts` — smoke test for both pipes

**Modified files (10):**
- 4 template files — swap `| date:'...'` for `| appDate` or `| appDateTime`
- 4 inline-template component files — same swap
- 5 component `.ts` files — replace `formatDate()` method bodies with the utility
- 2 files with duplicated `toYMD`/`addDays` — import from utility

Each task below is self-contained and ends with a commit.

---

## Task 1: Create `date-format.ts` utility + failing tests

**Files:**
- Create: `frontend/src/app/core/utils/date-format.spec.ts`
- Create: `frontend/src/app/core/utils/date-format.ts`

- [ ] **Step 1: Write the failing tests**

```typescript
// frontend/src/app/core/utils/date-format.spec.ts
import {
  formatDate,
  formatDateTime,
  toYMD,
  addDays,
  parseYMD,
} from './date-format';

describe('date-format utility', () => {
  describe('formatDate', () => {
    it('formats an ISO date string as dd/MM/yyyy', () => {
      expect(formatDate('2026-04-18')).toBe('18/04/2026');
    });

    it('formats an ISO datetime string dropping the time part', () => {
      expect(formatDate('2026-04-18T14:30:00')).toBe('18/04/2026');
    });

    it('formats a Date object as dd/MM/yyyy', () => {
      expect(formatDate(new Date(2026, 3, 18))).toBe('18/04/2026');
    });

    it('returns empty string for null or undefined', () => {
      expect(formatDate(null)).toBe('');
      expect(formatDate(undefined)).toBe('');
    });

    it('returns empty string for an invalid date string', () => {
      expect(formatDate('not-a-date')).toBe('');
    });
  });

  describe('formatDateTime', () => {
    it('formats an ISO datetime string as dd/MM/yyyy HH:mm', () => {
      expect(formatDateTime('2026-04-18T14:30:00')).toBe('18/04/2026 14:30');
    });

    it('returns empty string for null', () => {
      expect(formatDateTime(null)).toBe('');
    });

    it('pads hours and minutes with leading zero', () => {
      expect(formatDateTime('2026-04-18T04:05:00')).toBe('18/04/2026 04:05');
    });
  });

  describe('toYMD', () => {
    it('returns YYYY-MM-DD for a Date', () => {
      expect(toYMD(new Date(2026, 3, 18))).toBe('2026-04-18');
    });

    it('pads single-digit months and days', () => {
      expect(toYMD(new Date(2026, 0, 5))).toBe('2026-01-05');
    });
  });

  describe('addDays', () => {
    it('returns a new Date n days later', () => {
      const result = addDays(new Date(2026, 3, 18), 2);
      expect(result.getDate()).toBe(20);
      expect(result.getMonth()).toBe(3);
    });

    it('accepts negative deltas', () => {
      const result = addDays(new Date(2026, 3, 1), -5);
      expect(result.getMonth()).toBe(2);
      expect(result.getDate()).toBe(27);
    });

    it('does not mutate the original Date', () => {
      const original = new Date(2026, 3, 18);
      addDays(original, 10);
      expect(original.getDate()).toBe(18);
    });
  });

  describe('parseYMD', () => {
    it('parses a YYYY-MM-DD string into a Date', () => {
      const result = parseYMD('2026-04-18');
      expect(result).not.toBeNull();
      expect(result?.getFullYear()).toBe(2026);
      expect(result?.getMonth()).toBe(3);
      expect(result?.getDate()).toBe(18);
    });

    it('returns null for a malformed string', () => {
      expect(parseYMD('invalid')).toBeNull();
      expect(parseYMD('2026-13-01')).toBeNull();
    });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/date-format.spec.ts' --watch=false
```

Expected: FAIL with `Cannot find module './date-format'`.

- [ ] **Step 3: Create the utility**

```typescript
// frontend/src/app/core/utils/date-format.ts
export type DateInput = string | Date | number | null | undefined;

export function formatDate(input: DateInput): string {
  const d = toDate(input);
  if (!d) return '';
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

export function formatDateTime(input: DateInput): string {
  const d = toDate(input);
  if (!d) return '';
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function toYMD(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(d: Date, n: number): Date {
  const result = new Date(d);
  result.setDate(result.getDate() + n);
  return result;
}

export function parseYMD(ymd: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  const d = new Date(year, month - 1, day);
  if (d.getFullYear() !== year || d.getMonth() !== month - 1 || d.getDate() !== day) return null;
  return d;
}

function toDate(input: DateInput): Date | null {
  if (input === null || input === undefined) return null;
  const d = input instanceof Date ? new Date(input) : new Date(input);
  if (isNaN(d.getTime())) return null;
  return d;
}

function pad(n: number): string {
  return n.toString().padStart(2, '0');
}
```

- [ ] **Step 4: Run tests to verify 11/11 PASS**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/date-format.spec.ts' --watch=false
```

Expected: 11/11 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/utils/date-format.ts frontend/src/app/core/utils/date-format.spec.ts
git commit -m "feat: add shared date-format utility with tests"
```

---

## Task 2: Create `AppDatePipe` and `AppDateTimePipe`

**Files:**
- Create: `frontend/src/app/shared/pipes/app-date.pipe.ts`
- Create: `frontend/src/app/shared/pipes/app-datetime.pipe.ts`
- Create: `frontend/src/app/shared/pipes/app-date.pipe.spec.ts`

- [ ] **Step 1: Create `AppDatePipe`**

```typescript
// frontend/src/app/shared/pipes/app-date.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';
import { formatDate, type DateInput } from '../../core/utils/date-format';

@Pipe({ name: 'appDate', standalone: true, pure: true })
export class AppDatePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatDate(value);
  }
}
```

- [ ] **Step 2: Create `AppDateTimePipe`**

```typescript
// frontend/src/app/shared/pipes/app-datetime.pipe.ts
import { Pipe, PipeTransform } from '@angular/core';
import { formatDateTime, type DateInput } from '../../core/utils/date-format';

@Pipe({ name: 'appDateTime', standalone: true, pure: true })
export class AppDateTimePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatDateTime(value);
  }
}
```

- [ ] **Step 3: Create tests**

```typescript
// frontend/src/app/shared/pipes/app-date.pipe.spec.ts
import { AppDatePipe } from './app-date.pipe';
import { AppDateTimePipe } from './app-datetime.pipe';

describe('AppDatePipe', () => {
  const pipe = new AppDatePipe();

  it('delegates to formatDate', () => {
    expect(pipe.transform('2026-04-18')).toBe('18/04/2026');
    expect(pipe.transform(null)).toBe('');
  });
});

describe('AppDateTimePipe', () => {
  const pipe = new AppDateTimePipe();

  it('delegates to formatDateTime', () => {
    expect(pipe.transform('2026-04-18T14:30:00')).toBe('18/04/2026 14:30');
    expect(pipe.transform(null)).toBe('');
  });
});
```

- [ ] **Step 4: Run tests**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/app-date.pipe.spec.ts' --watch=false
```

Expected: 2/2 PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/pipes/
git commit -m "feat: add AppDatePipe and AppDateTimePipe"
```

---

## Task 3: Replace `| date` in `leaves.component.html`

**Files:**
- Modify: `frontend/src/app/features/leaves/leaves.component.html`
- Modify: `frontend/src/app/features/leaves/leaves.component.ts` (to add pipe import)

- [ ] **Step 1: Open `leaves.component.ts` and add the pipe import**

Add at the top of `leaves.component.ts`:
```typescript
import { AppDatePipe } from '../../shared/pipes/app-date.pipe';
```

Add `AppDatePipe` to the `imports` array of the `@Component` decorator. If `DatePipe` from `@angular/common` is imported (check the existing imports) AND no longer used anywhere else in the component's template/TS, remove it.

- [ ] **Step 2: Edit `leaves.component.html`**

Replace these three occurrences:

Line 102:
```html
{{ leave.startDate | date: 'dd/MM/yyyy' }} — {{ leave.endDate | date: 'dd/MM/yyyy' }}
```
with:
```html
{{ leave.startDate | appDate }} — {{ leave.endDate | appDate }}
```

Line 161 — same replacement.

Line 198:
```html
{{ 'pro.leaves.reviewedAt' | transloco }} {{ leave.reviewedAt | date: 'dd/MM/yyyy' }}
```
with:
```html
{{ 'pro.leaves.reviewedAt' | transloco }} {{ leave.reviewedAt | appDate }}
```

- [ ] **Step 3: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/leaves/
git commit -m "refactor: use appDate pipe in leaves component"
```

---

## Task 4: Replace `| date` in tracking components (4 files, inline templates)

**Files:**
- Modify: `frontend/src/app/features/tracking/components/client-info/client-info.component.ts`
- Modify: `frontend/src/app/features/tracking/components/visit-card/visit-card.component.ts`
- Modify: `frontend/src/app/features/tracking/components/client-notes/client-notes.component.ts`
- Modify: `frontend/src/app/features/tracking/components/client-bookings/client-bookings.component.ts`

These are inline-template components that use `| date:'mediumDate'` for timestamps and `| date:'d MMM'` for appointment dates. The spec treats `updatedAt`/`visitDate` fields as timestamps → `appDateTime`. Appointment dates stay a date → `appDate`.

**Important:** `client-header.component.ts` also uses `| date:'MMMM yyyy'` but is EXPLICITLY out of scope — leave it alone.

- [ ] **Step 1: Edit `client-info.component.ts`**

At line 68, swap:
```typescript
{{ profile().updatedAt | date:'mediumDate' }}
```
for:
```typescript
{{ profile().updatedAt | appDateTime }}
```

Add the import:
```typescript
import { AppDateTimePipe } from '../../../../shared/pipes/app-datetime.pipe';
```

Add `AppDateTimePipe` to the component's `imports` array. If `DatePipe` is imported and not used elsewhere in this file, remove it.

- [ ] **Step 2: Edit `visit-card.component.ts`**

At line 14, swap:
```typescript
{{ visit().visitDate | date:'mediumDate' }}
```
for:
```typescript
{{ visit().visitDate | appDateTime }}
```

Add the same `AppDateTimePipe` import + imports-array entry.

- [ ] **Step 3: Edit `client-notes.component.ts`**

At line 34, swap:
```typescript
{{ updatedAt() | date:'mediumDate' }}
```
for:
```typescript
{{ updatedAt() | appDateTime }}
```

Add the same `AppDateTimePipe` import + imports-array entry.

- [ ] **Step 4: Edit `client-bookings.component.ts`**

At lines 83 and 106, swap:
```typescript
{{ booking.appointmentDate | date:'d MMM' }}
```
for:
```typescript
{{ booking.appointmentDate | appDate }}
```

Add the import:
```typescript
import { AppDatePipe } from '../../../../shared/pipes/app-date.pipe';
```

Add `AppDatePipe` to the component's `imports` array.

- [ ] **Step 5: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/tracking/components/
git commit -m "refactor: use appDate/appDateTime pipes in tracking components"
```

---

## Task 5: Replace `| date` in notifications & pro-settings

**Files:**
- Modify: `frontend/src/app/pages/notifications/notifications.component.ts` (the spec path)
- Modify: `frontend/src/app/pages/notifications/notifications.component.html`
- Modify: `frontend/src/app/pages/pro/pro-settings.component.ts`

- [ ] **Step 1: Locate the notifications component**

Run:
```
grep -rn "AppDatePipe\|notifications.component" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/notifications --include="*.ts" | head -5
```

Identify whether `notifications.component` has an inline or external template. Open the corresponding file.

- [ ] **Step 2: Swap in notifications**

The HTML at `pages/notifications/notifications.component.html` line 46 has:
```html
<span class="notif-time">{{ notif.createdAt | date:'short' }}</span>
```

Change to:
```html
<span class="notif-time">{{ notif.createdAt | appDateTime }}</span>
```

In the component TS, add:
```typescript
import { AppDateTimePipe } from '../../shared/pipes/app-datetime.pipe';
```

And add `AppDateTimePipe` to the `imports` array. Remove `DatePipe` from imports if unused after this change.

- [ ] **Step 3: Pro-settings — evaluate the holiday badge**

`pro-settings.component.ts` has at lines 142-143:
```html
<span class="holiday-day">{{ holiday.date | date:'d' }}</span>
<span class="holiday-month">{{ holiday.date | date:'MMM' }}</span>
```

These are small badge fragments (day number + 3-letter month abbreviation) — a visual mini-calendar tile, not a full date. Per spec: these are OUT OF SCOPE (standalone `'d'`/`'MMM'` badges). **DO NOT change them.** Leave as-is.

- [ ] **Step 4: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/notifications/
git commit -m "refactor: use appDateTime pipe in notifications"
```

---

## Task 6: Consolidate `toYMD`/`addDays` in pro-booking-history

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-booking-history/booking-history.store.ts`
- Modify: `frontend/src/app/pages/pro/pro-booking-history/filters/period-filter-sheet.component.ts`

- [ ] **Step 1: Open `booking-history.store.ts`**

Locate the local `today()`, `daysAgo()`, `toYMD()`, and `formatDay()` helper functions (near the top and bottom of the file). You'll remove the `toYMD` local definition and add an import.

- [ ] **Step 2: Add the import in `booking-history.store.ts`**

Add near the existing imports:
```typescript
import { toYMD, addDays, formatDate } from '../../../core/utils/date-format';
```

- [ ] **Step 3: Remove the local `toYMD` function**

Delete the local `function toYMD(d: Date): string { ... }` block. The imported `toYMD` has an identical signature and behavior.

- [ ] **Step 4: Simplify the `daysAgo` helper**

Replace this local helper:
```typescript
function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return toYMD(d);
}
```
with:
```typescript
function daysAgo(n: number): string {
  return toYMD(addDays(new Date(), -n));
}
```

- [ ] **Step 5: Keep or replace `today()` local**

Keep the local `today()` function — it's still readable and only one line. (The alternative `toYMD(new Date())` inline would work too but doesn't matter for consistency.)

- [ ] **Step 6: Replace the local `formatDay` function with a shared `formatDate` call**

Find this local function:
```typescript
function formatDay(ymd: string): string {
  const [y, m, d] = ymd.split('-');
  return `${d}/${m}/${y}`;
}
```

Delete it. In `groupByDay()`, change the `label` computation:
```typescript
label: formatDay(date),
```
to:
```typescript
label: formatDate(date),
```

- [ ] **Step 7: Open `period-filter-sheet.component.ts`**

Remove the two local helper functions at the end of the file:
```typescript
function addDays(d: Date, days: number): Date { ... }
function toYMD(d: Date): string { ... }
```

Add at the top of the file with existing imports:
```typescript
import { toYMD, addDays } from '../../../../core/utils/date-format';
```

- [ ] **Step 8: Run existing store tests to confirm no regression**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/booking-history.store.spec.ts' --watch=false 2>&1 | tail -15
```

Expected: 7/7 PASS.

- [ ] **Step 9: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/
git commit -m "refactor: use shared date-format utility in booking history"
```

---

## Task 7: Refactor `pro-posts.component.ts` formatDate method

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-posts.component.ts`

- [ ] **Step 1: Locate the `formatDate` method**

Run:
```
grep -n "formatDate" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/pro/pro-posts.component.ts | head -10
```

You'll find a `protected formatDate(...)` method. Read its body (it uses `toLocaleDateString(getActiveLang())`).

- [ ] **Step 2: Replace its body**

Import at the top:
```typescript
import { formatDate as formatDateUtil } from '../../core/utils/date-format';
```

Replace the body of the `formatDate(input: ...)` method with:
```typescript
return formatDateUtil(input);
```

Remove any now-unused imports (`getActiveLang`, unused Transloco helpers, etc.). Verify nothing else in the file relies on them.

- [ ] **Step 3: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/pro/pro-posts.component.ts
git commit -m "refactor: use shared formatDate in pro-posts"
```

---

## Task 8: Refactor `pro-dashboard.component.ts` formatDateLabel

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts`

- [ ] **Step 1: Locate `formatDateLabel`**

Run:
```
grep -n "formatDateLabel" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/pro/pro-dashboard.component.ts
```

- [ ] **Step 2: Read the method body**

Open the file at those lines. The method parses a `YYYY-MM-DD` string and returns `DD/MM` (day/month only, WITHOUT year).

- [ ] **Step 3: Decide the semantics**

The utility's `formatDate` returns `DD/MM/YYYY`. The current method returns `DD/MM` (no year). These are different. The label is a compact "DD/MM" that appears as a chip/badge where fitting the year is tight.

Check the usage of `formatDateLabel` in the template: run
```
grep -n "formatDateLabel" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/pro/pro-dashboard.component.ts
```

If it's only used in compact badge contexts (small chips, limited width), keep the local implementation but refactor it to use `parseYMD` from the utility for consistency:

```typescript
import { parseYMD } from '../../core/utils/date-format';

protected formatDateLabel(ymd: string): string {
  const d = parseYMD(ymd);
  if (!d) return '';
  return `${d.getDate().toString().padStart(2, '0')}/${(d.getMonth() + 1).toString().padStart(2, '0')}`;
}
```

If it's used where a full date would make sense (test by reading the surrounding template), replace with the full `formatDate` from the utility:

```typescript
import { formatDate } from '../../core/utils/date-format';

protected formatDateLabel(ymd: string): string {
  return formatDate(ymd);
}
```

Pick ONE based on where it's rendered. Default: keep DD/MM for compact chips.

- [ ] **Step 4: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.ts
git commit -m "refactor: use shared parseYMD in pro-dashboard"
```

---

## Task 9: Refactor `pro-bookings.component.ts` buildDayLabel

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-bookings.component.ts`

- [ ] **Step 1: Locate `buildDayLabel`**

Run:
```
grep -n "buildDayLabel\|isToday\|isTomorrow" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/pro/pro-bookings.component.ts | head -10
```

- [ ] **Step 2: Read the method**

Open the file. The method returns one of three things based on context:
1. "Aujourd'hui" translation when isToday
2. "Demain" translation when isTomorrow
3. Otherwise a combination of weekday + date (e.g. "Mercredi 14/04/2026")

- [ ] **Step 3: Preserve weekday logic, swap only the date portion**

The weekday name (Mercredi, Jeudi...) uses `toLocaleDateString(getActiveLang(), { weekday: 'long' })`. KEEP THAT CALL.

In the branch that currently builds `DD/MM/YYYY` manually (likely via `padStart` concatenation or `Intl.DateTimeFormat`), replace the date-formatting fragment with a call to the shared `formatDate` utility:

Import at the top:
```typescript
import { formatDate as formatDateUtil } from '../../core/utils/date-format';
```

Where the method currently builds the date string (likely something like `` `${d.getDate().toString().padStart(2, '0')}/${...}` ``), replace with:
```typescript
formatDateUtil(d)
```

The final concatenation stays `${weekdayName} ${formatDateUtil(d)}` so the label reads "Mercredi 18/04/2026".

- [ ] **Step 4: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/pro/pro-bookings.component.ts
git commit -m "refactor: use shared formatDate in pro-bookings day label"
```

---

## Task 10: Refactor `bookings-drawer.component.ts`

**Files:**
- Modify: `frontend/src/app/features/bookings/components/bookings-drawer/bookings-drawer.component.ts` (exact path may vary)

- [ ] **Step 1: Locate the file**

Run:
```
grep -rn "class BookingsDrawerComponent\|bookings-drawer" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app --include="*.ts" | head -5
```

Open the discovered file.

- [ ] **Step 2: Locate `formatDate`**

Grep within the file for `formatDate`. Read its body — it uses `Intl.DateTimeFormat`.

- [ ] **Step 3: Replace its body**

Import at the top:
```typescript
import { formatDate as formatDateUtil } from '<relative-path>/core/utils/date-format';
```

Adjust the relative path based on the file's location. Replace the method body with:
```typescript
return formatDateUtil(input);
```

Remove any now-unused imports (like an `Intl` helper).

- [ ] **Step 4: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add <the file you modified>
git commit -m "refactor: use shared formatDate in bookings-drawer"
```

---

## Task 11: Refactor `employee-leaves.component.ts`

**Files:**
- Modify: `frontend/src/app/pages/pro/employee-leaves.component.ts`

- [ ] **Step 1: Locate methods**

Run:
```
grep -n "formatDate\|formatDisplayDate\|toYMD" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/pages/pro/employee-leaves.component.ts
```

- [ ] **Step 2: Replace `formatDate` body**

If `formatDate(input)` returns YYYY-MM-DD (inspect body), replace with:
```typescript
import { toYMD } from '../../core/utils/date-format';

protected formatDate(input: Date | string): string {
  const d = typeof input === 'string' ? new Date(input) : input;
  return toYMD(d);
}
```

If `formatDate(input)` returns DD/MM/YYYY for display, replace with:
```typescript
import { formatDate as formatDateUtil } from '../../core/utils/date-format';

protected formatDate(input: Date | string): string {
  return formatDateUtil(input);
}
```

- [ ] **Step 3: Replace `formatDisplayDate` body**

If present, it formats for display. Replace body with:
```typescript
return formatDateUtil(input);
```

(Make sure `formatDateUtil` is already imported from Step 2.)

- [ ] **Step 4: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/pro/employee-leaves.component.ts
git commit -m "refactor: use shared formatDate in employee-leaves"
```

---

## Task 12: Final sweep — catch any remaining `| date` calls

**Files:** audit only, modifications if anything was missed.

- [ ] **Step 1: Run the audit grep**

```
grep -rn "| date:" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app --include="*.html" --include="*.ts" | grep -v spec.ts
```

- [ ] **Step 2: For each result, decide**

Expected remaining usages (all in scope of "out of scope" decisions from spec):
- `client-header.component.ts` — `date:'MMMM yyyy'` — KEEP (semantic: "since month year", not a full date)
- `pro-settings.component.ts` — `date:'d'` and `date:'MMM'` — KEEP (badge fragments)

If ANY other `| date:` result appears, convert it:
- If the format includes a year (e.g. `'dd/MM/yyyy'`, `'mediumDate'`, `'full'`) → `| appDate`
- If the format includes an hour (e.g. `'short'`, `'medium'`, `'H:mm'`, `'HH:mm'`) → `| appDateTime`

Update the template, add the import, and `imports` array entry for the pipe.

- [ ] **Step 3: Build**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -15
```

Expected: build succeeds.

- [ ] **Step 4: Commit only if changes were made**

```bash
git add -A
git commit -m "refactor: wrap remaining date pipes to appDate/appDateTime"
```

If no changes: skip the commit.

---

## Task 13: Manual QA

**Files:** none.

- [ ] **Step 1: Start the dev server**

```
cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm start -- --port 4200
```

Wait for `Local: http://localhost:4200/`.

- [ ] **Step 2: Open the app in a browser**

Log in as a pro. Navigate through each page and visually check the date format on key screens:

- `/pro/dashboard` — day labels on stat chips
- `/pro/bookings` — group labels (e.g., "Mercredi 18/04/2026"), today/tomorrow tags, card meta
- `/pro/settings/history` — filter period chip (compact `DD/MM`), day group labels (`DD/MM/YYYY`)
- `/pro/leaves` or `/pro/employees/leaves` — leave cards: date range `DD/MM/YYYY`
- Client evolution page → audit-trail (visit cards, client-info, client-notes) — should show `DD/MM/YYYY HH:mm`
- Client-bookings mini list — appointment dates should be `DD/MM/YYYY`
- `/notifications` — notification time should be `DD/MM/YYYY HH:mm`

- [ ] **Step 3: Verify intentionally unchanged items**

- Client-header "Client depuis avril 2026" label — should STILL read as month + year, NOT `DD/MM/YYYY` (expected).
- Holiday badges in pro-settings — should STILL be small tiles with day number + short month name (expected).
- `HH:mm` time-only displays (e.g. booking time `14:30`) — unchanged.

- [ ] **Step 4: No commit if all checks pass**

If a regression is found, fix and commit a corrective patch.

---

## Self-Review

**Spec coverage:**
- ✅ Utility + 2 pipes → Tasks 1, 2
- ✅ Template replacements (leaves, tracking, notifications) → Tasks 3, 4, 5
- ✅ Helper consolidation (booking-history, period-filter-sheet) → Task 6
- ✅ Component method refactors (pro-posts, pro-dashboard, pro-bookings, bookings-drawer, employee-leaves) → Tasks 7, 8, 9, 10, 11
- ✅ Final sweep → Task 12
- ✅ Manual QA → Task 13
- ✅ Out-of-scope items (client-header `MMMM yyyy`, pro-settings `'d'`/`'MMM'` badges) — preserved by explicit instructions in Tasks 5 and 12

**Placeholder check:** None — every step has actual code or an actual command.

**Type consistency:** `DateInput`, `formatDate`, `formatDateTime`, `toYMD`, `addDays`, `parseYMD`, `AppDatePipe`, `AppDateTimePipe` — consistent across all tasks.

**Naming of `formatDate` import vs component method:** Most component refactors import the utility as `formatDate as formatDateUtil` to avoid shadowing the component's own `formatDate` method. This is spelled out in each relevant task.
