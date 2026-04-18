# Unified Date Format — Design Spec

**Date:** 2026-04-18
**Status:** Approved (pending user review)
**Scope:** Frontend only. Introduces a shared date-formatting utility and two
pipes, then replaces ad-hoc formatting across the app.

## Goal

Make every full-date display in the app render as `dd/MM/yyyy` and every
timestamp display as `dd/MM/yyyy HH:mm`. Keep weekday labels ("Lundi",
"Mardi"...) and relative labels ("Aujourd'hui", "Demain") intact. Consolidate
duplicated `toYMD` / `addDays` helpers into a single utility.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Scope | Entire app (pro + client + salon) |
| Date format | `dd/MM/yyyy` (e.g. `18/04/2026`) |
| Timestamp format | `dd/MM/yyyy HH:mm` (e.g. `18/04/2026 14:30`) |
| Weekday labels | Kept (Lundi, Mardi...) |
| Relative labels | Kept (Today / Tomorrow) |
| `MMMM yyyy` header label | Kept unchanged (semantic: "since month year", not a date) |
| `HH:mm` standalone times | Kept unchanged (already extracted from ISO) |

## Architecture

Three new artifacts, one refactor pass.

### 1. `core/utils/date-format.ts`

Pure TypeScript utility — no Angular dependency, no `Intl` call (SSR-safe,
deterministic).

```typescript
export function formatDate(input: DateInput): string;
export function formatDateTime(input: DateInput): string;
export function toYMD(d: Date): string;
export function addDays(d: Date, n: number): Date;
export function parseYMD(ymd: string): Date | null;

type DateInput = string | Date | number | null | undefined;
```

**Contract:**
- `formatDate(null | undefined | invalid)` → `""`
- `formatDate('2026-04-18')` → `"18/04/2026"`
- `formatDate('2026-04-18T14:30:00')` → `"18/04/2026"` (time omitted)
- `formatDate(new Date(2026, 3, 18))` → `"18/04/2026"`
- `formatDate(1744934400000)` → `"18/04/2026"` (epoch ms)
- `formatDateTime('2026-04-18T14:30:00')` → `"18/04/2026 14:30"`
- `formatDateTime(null)` → `""`
- `toYMD(new Date(2026, 3, 18))` → `"2026-04-18"`
- `addDays(new Date(2026, 3, 18), -30)` → `Date(2026, 2, 19)`
- `parseYMD("2026-04-18")` → `Date(2026, 3, 18)` at local midnight
- `parseYMD("not-a-date")` → `null`

**Implementation notes:**
- `formatDate`/`formatDateTime` internally normalise input to a `Date`, detect
  invalid dates (`isNaN(d.getTime())`), and build the output string with
  manual padding. No locale involved.
- `toYMD`/`addDays`/`parseYMD` exist today duplicated in 2+ components — this
  consolidation removes that duplication.

### 2. `shared/pipes/app-date.pipe.ts`

```typescript
@Pipe({ name: 'appDate', standalone: true, pure: true })
export class AppDatePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatDate(value);
  }
}
```

### 3. `shared/pipes/app-datetime.pipe.ts`

```typescript
@Pipe({ name: 'appDateTime', standalone: true, pure: true })
export class AppDateTimePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatDateTime(value);
  }
}
```

## Refactor plan

### A. Template replacements (`| date:'...'` → `| appDate` or `| appDateTime`)

From the exploration, the templates using `| date` today:

| File | Current format | New pipe |
|---|---|---|
| `features/leaves/leaves.component.html` (multiple lines) | `'dd/MM/yyyy'` | `appDate` |
| `features/tracking/components/client-bookings/*` | `'d MMM'` | `appDate` |
| `pages/client-evolution/audit-trail/visit-card.component.ts` | `'mediumDate'` | `appDate` |
| `pages/client-evolution/audit-trail/client-info.component.ts` | `'mediumDate'` | `appDate` |
| `pages/client-evolution/audit-trail/client-notes.component.ts` | `'mediumDate'` | `appDate` |
| `features/tracking/components/client-header/...` | `'MMMM yyyy'` | **KEPT** (not a full date) |
| `pages/pro/pro-settings.component.ts` | `'d'`, `'MMM'` | `appDate` for full-date uses; `'d'` / `'MMM'` standalone badges kept |
| `features/notifications/notifications.component.ts` | `'short'` | `appDateTime` |

For timestamps (a date+time value displayed as a timestamp, like audit-trail
entries), swap to `appDateTime` instead of `appDate`. The exploration found 3
audit-trail components using `'mediumDate'` against `createdAt` ISO strings —
those render as `appDateTime`.

### B. Component-level refactors

Five components with custom `formatDate()`/`formatDateLabel()` methods:

| File | Action |
|---|---|
| `pages/pro/pro-posts.component.ts` | Replace body with `formatDate(input)` from utility |
| `pages/pro/pro-dashboard.component.ts` — `formatDateLabel()` | Same |
| `pages/pro/pro-bookings.component.ts` — `buildDayLabel()` | Keep the today/tomorrow/weekday logic; replace the date-formatting branch with `formatDate(d)` |
| `features/bookings/components/bookings-drawer/...` — `formatDate()` | Same |
| `pages/pro/employee-leaves.component.ts` — `formatDate()` + `formatDisplayDate()` | Same |

### C. Helper consolidation

Two files have local `toYMD`/`addDays` duplicates — replace with imports from
`core/utils/date-format.ts`:

- `pages/pro/pro-booking-history/booking-history.store.ts`
- `pages/pro/pro-booking-history/filters/period-filter-sheet.component.ts`

## i18n impact

Zero. The format is locale-agnostic by design (always `dd/MM/yyyy`), so the
Transloco language switch doesn't affect it. Weekday labels and
"Today"/"Tomorrow" remain Transloco-driven.

## Testing

### Unit (Jasmine/Karma)

**`date-format.spec.ts`** (6 cases):
- `formatDate('2026-04-18')` returns `'18/04/2026'`
- `formatDate('2026-04-18T14:30:00')` returns `'18/04/2026'` (drops time)
- `formatDate(null)` returns `''`
- `formatDate('invalid')` returns `''`
- `formatDateTime('2026-04-18T14:30:00')` returns `'18/04/2026 14:30'`
- `formatDateTime(null)` returns `''`
- `toYMD(new Date(2026, 3, 18))` returns `'2026-04-18'`
- `addDays(new Date(2026, 3, 18), -30)` returns a Date in March 2026

**`app-date.pipe.spec.ts`** (1 smoke case): pipe delegates to `formatDate`.

**`app-datetime.pipe.spec.ts`** (1 smoke case): pipe delegates to `formatDateTime`.

### Manual QA

After refactor, spot-check 5 pages:
- `/pro/dashboard` — day labels
- `/pro/bookings` — day group labels + card dates
- `/pro/settings/history` — filter period chip (`dd/MM → dd/MM`), day group labels
- `/pro/employees/leaves` — leave start/end dates
- Client evolution audit trail — timestamps display as `dd/MM/yyyy HH:mm`

Verify weekday labels ("Lundi", "Mardi"...) still appear where expected.

## Out of scope

- Period chip format in `pro-booking-history` (`dd/MM → dd/MM`) — kept as-is;
  it's intentionally compact
- Time-only displays (`HH:mm` extracted from `appointmentTime`) — no change
- Locale switching: the format is locale-agnostic on purpose
- Calendar/datepicker popup format — native Material widget, untouched

## Rollout

Single PR. Order:

1. Add utility + 2 pipes + unit tests.
2. Refactor templates (A).
3. Refactor component methods (B).
4. Consolidate duplicated helpers (C).
5. Manual QA.
