# Pro Booking History Page — Design Spec

**Date:** 2026-04-18
**Status:** Approved (pending user review)
**Scope:** Frontend only. Reuses existing backend endpoint `GET /api/bookings/detailed`.

## Goal

Add a "Historique des bookings" page accessible from the pro settings area
(`/pro/settings/history`). It lists past bookings of the salon with filters
(period, status, employee, client search), infinite-scroll pagination, and
navigation to each booking's client profile on tap.

## Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Statuses | All 4 shown by default, user can filter via multi-select |
| Default period | Last 30 days (today - 30 → today) |
| Navigation entry | Card inside `pro-settings.component.ts` → route `/pro/settings/history` |
| Filters | Status (multi) + custom date range + client search + employee filter |
| Action on row | Read-only. Tap → navigate to `/pro/clients/{userId}` |
| Pagination | Infinite scroll, 20 items per page, sorted `appointmentDate,desc` |
| Layout style | Toolbar horizontale (chips scrollables) |

## Architecture

Three new frontend artifacts, one extracted shared component, zero backend
changes. The existing `GET /api/bookings/detailed?status&from&to&userId&page&size&sort`
endpoint already supports everything needed.

### 1. Route

In `frontend/src/app/app.routes.ts`, add under the `/pro` pro-guarded block:

```typescript
{
  path: 'settings/history',
  loadComponent: () =>
    import('./pages/pro/pro-booking-history/pro-booking-history.component')
      .then(m => m.ProBookingHistoryComponent),
}
```

### 2. Navigation from settings

In `pro-settings.component.ts`, append a new card:

```html
<a class="settings-card" routerLink="/pro/settings/history">
  <mat-icon>history</mat-icon>
  <div class="card-content">
    <h3>{{ 'pro.settings.history.card.title' | transloco }}</h3>
    <p>{{ 'pro.settings.history.card.desc' | transloco }}</p>
  </div>
  <mat-icon class="chevron">chevron_right</mat-icon>
</a>
```

### 3. `BookingHistoryStore` (NgRx SignalStore)

Location: `frontend/src/app/pages/pro/pro-booking-history/booking-history.store.ts`

```typescript
interface Filters {
  statuses: CareBookingStatus[];  // default all 4
  from: string;                   // YYYY-MM-DD, default today - 30d
  to: string;                     // YYYY-MM-DD, default today
  clientQuery: string;            // debounced
  employeeId: number | null;      // null = all
}

interface State {
  items: CareBookingDetailed[];   // accumulated across pages
  page: number;                   // 0-based last loaded
  size: number;                   // 20
  hasMore: boolean;
  filters: Filters;
}
```

Pattern: `withState → withRequestStatus → withComputed → withMethods → withHooks(onInit)`.

**Methods:**
- `updateFilters(partial: Partial<Filters>)` — merges, resets `items=[]`, `page=0`, calls `loadPage(0)`
- `loadNextPage()` — if `hasMore && !isPending`, calls `loadPage(page + 1)`
- `searchClient(query: string)` — `rxMethod` with `debounceTime(300)`, `distinctUntilChanged`, invokes `updateFilters({ clientQuery: query })`
- `loadPage(n: number)` — calls `BookingsService.listDetailed({ ...filters, page: n, size: 20, sort: 'appointmentDate,desc' })`, concatenates response content to `items` (or replaces if `n === 0`), updates `hasMore = !response.last`

**Computed:**
- `groupedByDay: DayGroup[]` — items regrouped by `appointmentDate`
- `emptyState: boolean` — `items.length === 0 && !isPending`

**Client search handling:**
- When `clientQuery` length ≥ 2, store calls `SalonClientService.search(query)` in
  parallel. If match(es) found, use the first `id` as `userId` in the request.
  If no match, still issue the request with `clientQuery` mapped to a
  client-side filter on `salonClientName || user.name` after fetch.
  (Pragmatic: the detailed endpoint doesn't currently accept a name query.)

### 4. `ProBookingHistoryComponent` (standalone page)

Location: `frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts`

Providers: `[BookingHistoryStore, EmployeesStore]`

Template structure:

```
<header>
  <button back>← {{ 'pro.history.title' | transloco }}</button>
</header>

<input matInput debounced placeholder="Rechercher un client..." />

<div class="filters-row">
  <chip (click)="openPeriodSheet()">{{ periodLabel() }} ▾</chip>
  <chip (click)="openStatusSheet()">{{ statusLabel() }} ▾</chip>
  <chip (click)="openEmployeeSheet()">{{ employeeLabel() }} ▾</chip>
</div>

@if (store.isPending() && store.items().length === 0) {
  <app-booking-skeleton count="3" />
} @else if (store.emptyState()) {
  <app-empty-state icon="history" text="{{ 'pro.history.empty' | transloco }}" />
} @else {
  @for (group of store.groupedByDay(); track group.date) {
    <div class="day-group-label">{{ group.label }}</div>
    @for (b of group.items; track b.id) {
      <app-booking-card [booking]="b" (click)="openClient(b.user.id)" />
    }
  }
  <div #sentinel class="infinite-sentinel">
    @if (store.isPending()) {
      <mat-spinner diameter="24" />
    } @else if (!store.hasMore()) {
      <span>{{ 'pro.history.endOfList' | transloco }}</span>
    }
  </div>
}
```

Uses `IntersectionObserver` on `#sentinel` to call `store.loadNextPage()`. The
observer is set up in `afterNextRender()` for SSR safety.

### 5. Three bottom-sheets for filter UIs

Each uses the existing `bottomSheetConfig()` helper and `<app-sheet-handle />`
pattern from the previous chantier.

- **`PeriodFilterSheetComponent`** — radio list: `30j / 3mois / 6mois / Personnalisé`. "Personnalisé" reveals two `mat-datepicker` inputs for `from` / `to`.
- **`StatusFilterSheetComponent`** — checkbox list for the 4 statuses. Emits `CareBookingStatus[]` on confirm.
- **`EmployeeFilterSheetComponent`** — list fed by `EmployeesStore`, with "Tous" as the first item. Emits `employeeId | null` on confirm.

Each sheet returns its value via `dialogRef.close(value)`, and
`ProBookingHistoryComponent` merges it into the store via `updateFilters()`.

### 6. Shared `BookingCardComponent` (refactor)

Location: `frontend/src/app/features/bookings/components/booking-card/booking-card.component.ts`

Extracted from the current inline markup in `pro-bookings.component.ts`.
Renders: time, care name, client name (`salonClientName || user.name`),
employee name, status pill. Emits `(cardClick)` with the booking payload.

**Used by both `pro-bookings` and `pro-booking-history`** — keeps visual
consistency between the two pages.

## i18n keys

Added to `frontend/src/assets/i18n/fr.json` and `en.json`:

```
pro.history.title                    = "Historique des bookings" / "Booking history"
pro.history.empty                    = "Aucun booking dans cet historique" / "No bookings in this period"
pro.history.search                   = "Rechercher un client..." / "Search a client..."
pro.history.filter.period.30days     = "30 derniers jours" / "Last 30 days"
pro.history.filter.period.3months    = "3 derniers mois" / "Last 3 months"
pro.history.filter.period.6months    = "6 derniers mois" / "Last 6 months"
pro.history.filter.period.custom     = "Personnalisé" / "Custom"
pro.history.filter.period.from       = "Du" / "From"
pro.history.filter.period.to         = "Au" / "To"
pro.history.filter.status.all        = "Tous les statuts" / "All statuses"
pro.history.filter.status.selected   = "{{count}} sélectionnés" / "{{count}} selected"
pro.history.filter.employee.all      = "Tous les employés" / "All employees"
pro.history.endOfList                = "Plus rien à charger" / "End of list"
pro.settings.history.card.title      = "Historique des bookings" / "Booking history"
pro.settings.history.card.desc       = "Consulter les rendez-vous passés" / "Browse past appointments"
common.cancel                        = (reuse if exists)
common.apply                         = "Appliquer" / "Apply"
```

## Testing

### Unit (Jasmine/Karma)

**`BookingHistoryStore`:**
- Initial state has default filters (today-30 → today, all statuses, no employee)
- `updateFilters({ statuses: [CONFIRMED] })` resets `items` to `[]` and `page` to `0`
- `loadNextPage()` increments `page` and concatenates items when `hasMore`
- `loadNextPage()` is a no-op when `hasMore=false`
- `loadNextPage()` is a no-op when `isPending=true`
- `searchClient('abc')` debounces 300ms before firing

**`ProBookingHistoryComponent`:**
- Renders skeleton on initial load
- Renders empty state when items empty + not pending
- Click on `<app-booking-card>` calls `router.navigate(['/pro/clients', id])`

**`BookingCardComponent` (new shared):**
- Renders time, care name, name, employee, status pill for each status
- Emits `(cardClick)` with booking on host element click

### Manual

- iPhone SE (375×667) viewport
- Open /pro/settings/history
- Scroll to bottom → next page loads with spinner
- Pull each filter sheet, change values, list updates
- Search for "mar" → debounce → result filtered
- Tap row → navigates to `/pro/clients/{id}`
- Desktop ≥768px: layout stays usable (filters as chips horizontally)

## Out of scope

- CSV / PDF export
- Editing bookings from history (read-only — per decision Q5)
- Aggregated stats (counters) — variant C explicitly rejected
- Offline caching / local persistence
- The date format change to `jj/mm/aaaa` is its own chantier (next) — this page
  uses `dd/MM/yyyy` via Angular `DatePipe` as an interim, and will benefit from
  the global pipe when that work lands

## Rollout

Single PR. Order of operations:

1. Extract `BookingCardComponent` from `pro-bookings`; update `pro-bookings` to
   use it (no visual regression expected).
2. Add the store + page component + 3 filter sheets.
3. Add the settings card + route + i18n keys.
4. Manual QA on mobile viewport.
