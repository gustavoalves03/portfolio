# Pro Booking History Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a new "Historique des bookings" page at `/pro/settings/history` — lists past salon bookings with toolbar-chip filters (period / status / employee / client search), infinite scroll pagination, and tap-to-navigate to the client profile.

**Architecture:** Frontend-only. Reuses the existing `GET /api/bookings/detailed` backend endpoint. Three new files (page component, SignalStore, 3 filter bottom-sheets), one shared extraction (`BookingCardComponent` pulled out of `pro-bookings`), one settings card + one route.

**Tech Stack:** Angular 20 (standalone, zoneless), NgRx SignalStore, Angular Material (dialogs, datepicker, checkbox, form-field), Transloco (i18n), IntersectionObserver.

**Spec:** `docs/superpowers/specs/2026-04-18-pro-booking-history-design.md`

---

## Key constraint: backend supports only single-status filter

The existing `GET /api/bookings/detailed` accepts `status=X` as a single enum value. To keep this chantier frontend-only, multi-status filtering is handled like this:

- **0 or all 4 statuses selected** → no `status` parameter sent (backend returns all)
- **Exactly 1 status selected** → `status=X` sent to backend
- **2 or 3 statuses selected** → no `status` parameter sent; filtered client-side after response

This avoids a backend change and is pragmatic for the expected filter usage.

---

## File Structure

**New files (6):**
- `frontend/src/app/features/bookings/components/booking-card/booking-card.component.ts` — shared card extracted from `pro-bookings`
- `frontend/src/app/pages/pro/pro-booking-history/booking-history.store.ts` — SignalStore
- `frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts` — page component
- `frontend/src/app/pages/pro/pro-booking-history/filters/period-filter-sheet.component.ts`
- `frontend/src/app/pages/pro/pro-booking-history/filters/status-filter-sheet.component.ts`
- `frontend/src/app/pages/pro/pro-booking-history/filters/employee-filter-sheet.component.ts`

**New test files (2):**
- `frontend/src/app/pages/pro/pro-booking-history/booking-history.store.spec.ts`
- `frontend/src/app/features/bookings/components/booking-card/booking-card.component.spec.ts`

**Modified files (5):**
- `frontend/src/app/features/bookings/models/bookings.model.ts` — extend `BookingFilters` with optional `status: CareBookingStatus | null` kept as-is (no change; we reuse single-status)
- `frontend/src/app/pages/pro/pro-bookings.component.ts` — swap inline booking card markup with `<app-booking-card>`
- `frontend/src/app/pages/pro/pro-settings.component.ts` — add "Historique" card with routerLink
- `frontend/src/app/app.routes.ts` — add `/pro/settings/history` route
- `frontend/src/assets/i18n/fr.json` and `en.json` — new i18n keys

Each task below is self-contained and ends with a commit.

---

## Task 1: Extract `BookingCardComponent` (shared)

**Files:**
- Create: `frontend/src/app/features/bookings/components/booking-card/booking-card.component.ts`
- Create: `frontend/src/app/features/bookings/components/booking-card/booking-card.component.spec.ts`

- [ ] **Step 1: Create `BookingCardComponent`**

```typescript
// frontend/src/app/features/bookings/components/booking-card/booking-card.component.ts
import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { CareBookingDetailed, CareBookingStatus } from '../../models/bookings.model';

@Component({
  selector: 'app-booking-card',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  template: `
    <div class="card" (click)="cardClick.emit(booking())">
      <div class="time">{{ booking().appointmentTime.substring(0, 5) }}</div>
      <div class="info">
        <div class="care">{{ booking().care.name }}</div>
        <div class="people">
          <span class="client">{{ booking().salonClientName || booking().user.name }}</span>
          @if (booking().employeeName) {
            <span class="sep">·</span>
            <span class="employee">{{ booking().employeeName }}</span>
          }
        </div>
      </div>
      <span class="status" [class.ok]="booking().status === 'CONFIRMED'"
                         [class.pending]="booking().status === 'PENDING'"
                         [class.cancelled]="booking().status === 'CANCELLED'"
                         [class.noshow]="booking().status === 'NO_SHOW'">
        {{ 'bookings.status.' + booking().status | transloco }}
      </span>
    </div>
  `,
  styles: [`
    .card {
      background: white;
      border-radius: 10px;
      padding: 10px 12px;
      display: flex;
      gap: 10px;
      align-items: center;
      box-shadow: 0 1px 2px rgba(0,0,0,0.04);
      cursor: pointer;
      transition: background 150ms ease;
    }
    .card:hover { background: #fafafa; }
    .time { font-weight: 600; color: #c06; font-size: 13px; min-width: 44px; }
    .info { flex: 1; min-width: 0; }
    .care { font-weight: 500; color: #333; font-size: 13px; }
    .people { font-size: 11px; color: #666; margin-top: 2px; }
    .sep { margin: 0 4px; color: #aaa; }
    .status {
      font-size: 9px;
      padding: 3px 7px;
      border-radius: 5px;
      font-weight: 600;
      text-transform: uppercase;
    }
    .status.ok { background: #dcfce7; color: #166534; }
    .status.pending { background: #fef3c7; color: #92400e; }
    .status.cancelled { background: #f3f4f6; color: #6b7280; }
    .status.noshow { background: #fee2e2; color: #991b1b; }
  `],
})
export class BookingCardComponent {
  readonly booking = input.required<CareBookingDetailed>();
  readonly cardClick = output<CareBookingDetailed>();
}
```

- [ ] **Step 2: Write tests**

```typescript
// frontend/src/app/features/bookings/components/booking-card/booking-card.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideTransloco } from '@jsverse/transloco';
import { BookingCardComponent } from './booking-card.component';
import { CareBookingDetailed, CareBookingStatus } from '../../models/bookings.model';

function makeBooking(overrides: Partial<CareBookingDetailed> = {}): CareBookingDetailed {
  return {
    id: 1,
    user: { id: 42, name: 'Marie D.', email: 'm@x.fr' } as any,
    care: { id: 10, name: 'Soin visage', durationMinutes: 45, priceCents: 4500 } as any,
    quantity: 1,
    appointmentDate: '2026-04-17',
    appointmentTime: '14:30:00',
    status: CareBookingStatus.CONFIRMED,
    createdAt: '2026-04-10T10:00:00',
    employeeId: 5,
    employeeName: 'Sophie',
    salonClientId: null,
    salonClientName: null,
    ...overrides,
  };
}

describe('BookingCardComponent', () => {
  let fixture: ComponentFixture<BookingCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingCardComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideTransloco({
          config: { availableLangs: ['fr'], defaultLang: 'fr' },
          loader: { getTranslation: () => Promise.resolve({}) } as any,
        }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(BookingCardComponent);
  });

  it('renders appointment time, care name, and client name', () => {
    fixture.componentRef.setInput('booking', makeBooking());
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.time')?.textContent?.trim()).toBe('14:30');
    expect(host.querySelector('.care')?.textContent?.trim()).toBe('Soin visage');
    expect(host.querySelector('.client')?.textContent?.trim()).toBe('Marie D.');
  });

  it('shows salonClientName over user.name when present', () => {
    fixture.componentRef.setInput('booking', makeBooking({
      salonClientName: 'Julie R.',
      salonClientId: 99,
    }));
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.client')?.textContent?.trim()).toBe('Julie R.');
  });

  it('emits cardClick with the booking on host click', () => {
    const booking = makeBooking();
    fixture.componentRef.setInput('booking', booking);
    const emitted: CareBookingDetailed[] = [];
    fixture.componentInstance.cardClick.subscribe((b) => emitted.push(b));
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('.card') as HTMLElement;
    card.click();
    expect(emitted.length).toBe(1);
    expect(emitted[0].id).toBe(1);
  });

  it('applies the correct status class for each status', () => {
    const cases: Array<[CareBookingStatus, string]> = [
      [CareBookingStatus.CONFIRMED, 'ok'],
      [CareBookingStatus.PENDING, 'pending'],
      [CareBookingStatus.CANCELLED, 'cancelled'],
      [CareBookingStatus.NO_SHOW, 'noshow'],
    ];
    for (const [status, className] of cases) {
      fixture.componentRef.setInput('booking', makeBooking({ status }));
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;
      const pill = host.querySelector('.status') as HTMLElement;
      expect(pill.classList.contains(className)).toBeTrue();
    }
  });
});
```

- [ ] **Step 3: Run the tests**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/booking-card.component.spec.ts' --watch=false`

Expected: 4/4 PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/bookings/components/booking-card/
git commit -m "feat: extract shared BookingCardComponent"
```

---

## Task 2: Wire `BookingCardComponent` into `pro-bookings`

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-bookings.component.ts`

- [ ] **Step 1: Read the current file**

Open `frontend/src/app/pages/pro/pro-bookings.component.ts` and locate the template section that renders each booking. You'll see markup roughly like:

```html
<div class="booking-card" (click)="openClient(booking.user.id)">
  <div class="card-time">...</div>
  <div class="card-info">...</div>
  <span class="card-status ...">...</span>
</div>
```

This varies — read the actual file before editing.

- [ ] **Step 2: Add the import**

Add at the top of the file:
```typescript
import { BookingCardComponent } from '../../features/bookings/components/booking-card/booking-card.component';
```

Add `BookingCardComponent` to the `imports` array of the `@Component` decorator.

- [ ] **Step 3: Replace the inline card markup**

In the template's inner `@for (booking of group.bookings; track booking.id) { ... }` loop, replace the inline card with:

```html
<app-booking-card
  [booking]="booking"
  (cardClick)="openClient(booking.user.id)" />
```

Remove the corresponding inline `.booking-card` SCSS rules (time, info, people, status pills, etc.) that are now owned by `BookingCardComponent`. Keep unrelated styles.

- [ ] **Step 4: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -20`

Expected: build succeeds, no errors.

- [ ] **Step 5: Visual sanity check (optional, not required for CI)**

Run dev server and verify that `/pro/bookings` still looks correct.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/pro/pro-bookings.component.ts
git commit -m "refactor: use BookingCardComponent in pro-bookings"
```

---

## Task 3: Add i18n keys

**Files:**
- Modify: `frontend/src/assets/i18n/fr.json`
- Modify: `frontend/src/assets/i18n/en.json`

- [ ] **Step 1: Add keys to `fr.json`**

Merge the following keys into the existing JSON tree (put them in the right nested objects — `pro.history.*`, `pro.settings.history.*`, `common.apply`):

```json
{
  "pro": {
    "history": {
      "title": "Historique des bookings",
      "empty": "Aucun booking dans cet historique",
      "search": "Rechercher un client...",
      "filter": {
        "period": {
          "30days": "30 derniers jours",
          "3months": "3 derniers mois",
          "6months": "6 derniers mois",
          "custom": "Personnalisé",
          "from": "Du",
          "to": "Au"
        },
        "status": {
          "all": "Tous les statuts",
          "selected": "{{count}} sélectionnés"
        },
        "employee": {
          "all": "Tous les employés"
        }
      },
      "endOfList": "Plus rien à charger"
    },
    "settings": {
      "history": {
        "card": {
          "title": "Historique des bookings",
          "desc": "Consulter les rendez-vous passés"
        }
      }
    }
  },
  "common": {
    "apply": "Appliquer"
  }
}
```

- [ ] **Step 2: Add matching keys to `en.json`**

```json
{
  "pro": {
    "history": {
      "title": "Booking history",
      "empty": "No bookings in this period",
      "search": "Search a client...",
      "filter": {
        "period": {
          "30days": "Last 30 days",
          "3months": "Last 3 months",
          "6months": "Last 6 months",
          "custom": "Custom",
          "from": "From",
          "to": "To"
        },
        "status": {
          "all": "All statuses",
          "selected": "{{count}} selected"
        },
        "employee": {
          "all": "All employees"
        }
      },
      "endOfList": "End of list"
    },
    "settings": {
      "history": {
        "card": {
          "title": "Booking history",
          "desc": "Browse past appointments"
        }
      }
    }
  },
  "common": {
    "apply": "Apply"
  }
}
```

- [ ] **Step 3: Verify JSON syntax**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && node -e "JSON.parse(require('fs').readFileSync('src/assets/i18n/fr.json','utf8')); JSON.parse(require('fs').readFileSync('src/assets/i18n/en.json','utf8')); console.log('OK');"`

Expected: `OK`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/assets/i18n/fr.json frontend/src/assets/i18n/en.json
git commit -m "feat: add i18n keys for pro booking history"
```

---

## Task 4: Write `BookingHistoryStore` failing tests

**Files:**
- Create: `frontend/src/app/pages/pro/pro-booking-history/booking-history.store.spec.ts`

- [ ] **Step 1: Create the spec**

```typescript
// frontend/src/app/pages/pro/pro-booking-history/booking-history.store.spec.ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { BookingHistoryStore } from './booking-history.store';
import { BookingsService } from '../../../features/bookings/services/bookings.service';
import { SalonClientService } from '../../../features/salon-clients/salon-client.service';
import { CareBookingStatus, CareBookingDetailed } from '../../../features/bookings/models/bookings.model';

function makeBooking(id: number): CareBookingDetailed {
  return {
    id,
    user: { id: 100 + id, name: `Client ${id}`, email: `c${id}@x.fr` } as any,
    care: { id: 1, name: 'Soin', durationMinutes: 30, priceCents: 3000 } as any,
    quantity: 1,
    appointmentDate: '2026-04-01',
    appointmentTime: '10:00:00',
    status: CareBookingStatus.CONFIRMED,
    createdAt: '2026-03-01T10:00:00',
    employeeId: null,
    employeeName: null,
    salonClientId: null,
    salonClientName: null,
  };
}

describe('BookingHistoryStore', () => {
  let store: InstanceType<typeof BookingHistoryStore>;
  let bookingsService: jasmine.SpyObj<BookingsService>;
  let salonClientService: jasmine.SpyObj<SalonClientService>;

  beforeEach(() => {
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', ['listDetailed']);
    salonClientService = jasmine.createSpyObj<SalonClientService>('SalonClientService', ['search']);

    bookingsService.listDetailed.and.returnValue(of({
      content: [makeBooking(1), makeBooking(2)],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
      numberOfElements: 2,
      empty: false,
    } as any));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        BookingHistoryStore,
        { provide: BookingsService, useValue: bookingsService },
        { provide: SalonClientService, useValue: salonClientService },
      ],
    });
    store = TestBed.inject(BookingHistoryStore);
  });

  it('initial filters: 30 days range, all statuses, no employee, empty search', () => {
    const f = store.filters();
    expect(f.statuses.length).toBe(4);
    expect(f.employeeId).toBeNull();
    expect(f.clientQuery).toBe('');
    // from should be <= to
    expect(new Date(f.from).getTime()).toBeLessThanOrEqual(new Date(f.to).getTime());
  });

  it('updateFilters resets items and page, then reloads', () => {
    store.updateFilters({ statuses: [CareBookingStatus.CANCELLED] });
    expect(store.items().length).toBe(2); // from reload
    expect(store.page()).toBe(0);
    expect(bookingsService.listDetailed).toHaveBeenCalled();
  });

  it('loadNextPage is a no-op when hasMore is false', () => {
    bookingsService.listDetailed.calls.reset();
    expect(store.hasMore()).toBeFalse();
    store.loadNextPage();
    expect(bookingsService.listDetailed).not.toHaveBeenCalled();
  });

  it('loadNextPage appends items when hasMore is true', () => {
    // Make first response indicate there's a next page
    bookingsService.listDetailed.and.returnValue(of({
      content: [makeBooking(1), makeBooking(2)],
      totalElements: 4,
      totalPages: 2,
      number: 0,
      size: 2,
      first: true,
      last: false,
      numberOfElements: 2,
      empty: false,
    } as any));

    store.updateFilters({}); // triggers reload
    expect(store.hasMore()).toBeTrue();

    // Second page
    bookingsService.listDetailed.and.returnValue(of({
      content: [makeBooking(3), makeBooking(4)],
      totalElements: 4,
      totalPages: 2,
      number: 1,
      size: 2,
      first: false,
      last: true,
      numberOfElements: 2,
      empty: false,
    } as any));

    store.loadNextPage();
    expect(store.items().length).toBe(4);
    expect(store.page()).toBe(1);
    expect(store.hasMore()).toBeFalse();
  });

  it('sends status parameter when exactly 1 status is selected', () => {
    bookingsService.listDetailed.calls.reset();
    store.updateFilters({ statuses: [CareBookingStatus.NO_SHOW] });
    const [filters] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(filters?.status).toBe(CareBookingStatus.NO_SHOW);
  });

  it('omits status parameter when 0 or all 4 statuses selected', () => {
    bookingsService.listDetailed.calls.reset();
    store.updateFilters({ statuses: [] });
    const [f1] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(f1?.status).toBeUndefined();

    store.updateFilters({
      statuses: [
        CareBookingStatus.CONFIRMED,
        CareBookingStatus.PENDING,
        CareBookingStatus.CANCELLED,
        CareBookingStatus.NO_SHOW,
      ],
    });
    const [f2] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(f2?.status).toBeUndefined();
  });

  it('omits status parameter and filters client-side when 2-3 statuses selected', () => {
    bookingsService.listDetailed.and.returnValue(of({
      content: [
        { ...makeBooking(1), status: CareBookingStatus.CONFIRMED },
        { ...makeBooking(2), status: CareBookingStatus.NO_SHOW },
        { ...makeBooking(3), status: CareBookingStatus.CANCELLED },
      ],
      totalElements: 3, totalPages: 1, number: 0, size: 20,
      first: true, last: true, numberOfElements: 3, empty: false,
    } as any));

    bookingsService.listDetailed.calls.reset();
    store.updateFilters({
      statuses: [CareBookingStatus.CONFIRMED, CareBookingStatus.NO_SHOW],
    });
    const [filters] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(filters?.status).toBeUndefined();

    // Client-side filter: items should only have CONFIRMED and NO_SHOW
    const items = store.items();
    expect(items.every(i => i.status === CareBookingStatus.CONFIRMED || i.status === CareBookingStatus.NO_SHOW)).toBeTrue();
    expect(items.length).toBe(2);
  });
});
```

- [ ] **Step 2: Run tests — expect failure (module not found)**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/booking-history.store.spec.ts' --watch=false`

Expected: FAIL with `Cannot find module './booking-history.store'`.

- [ ] **Step 3: Commit the failing tests**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/booking-history.store.spec.ts
git commit -m "test: add failing spec for BookingHistoryStore"
```

---

## Task 5: Implement `BookingHistoryStore`

**Files:**
- Create: `frontend/src/app/pages/pro/pro-booking-history/booking-history.store.ts`

- [ ] **Step 1: Create the store**

```typescript
// frontend/src/app/pages/pro/pro-booking-history/booking-history.store.ts
import { computed, inject } from '@angular/core';
import {
  patchState,
  signalStore,
  withComputed,
  withHooks,
  withMethods,
  withState,
} from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { debounceTime, distinctUntilChanged, pipe, switchMap, tap } from 'rxjs';
import { withRequestStatus, setPending, setFulfilled, setError } from '../../../shared/features/request-status/request-status.feature';
import { BookingsService } from '../../../features/bookings/services/bookings.service';
import { SalonClientService } from '../../../features/salon-clients/salon-client.service';
import { CareBookingDetailed, CareBookingStatus, BookingFilters } from '../../../features/bookings/models/bookings.model';

export interface HistoryFilters {
  statuses: CareBookingStatus[];
  from: string; // YYYY-MM-DD
  to: string;   // YYYY-MM-DD
  clientQuery: string;
  employeeId: number | null;
}

interface HistoryState {
  items: CareBookingDetailed[];
  page: number;
  size: number;
  hasMore: boolean;
  filters: HistoryFilters;
}

const PAGE_SIZE = 20;
const ALL_STATUSES: CareBookingStatus[] = [
  CareBookingStatus.CONFIRMED,
  CareBookingStatus.PENDING,
  CareBookingStatus.CANCELLED,
  CareBookingStatus.NO_SHOW,
];

function today(): string {
  const d = new Date();
  return toYMD(d);
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return toYMD(d);
}

function toYMD(d: Date): string {
  const y = d.getFullYear();
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function initialFilters(): HistoryFilters {
  return {
    statuses: [...ALL_STATUSES],
    from: daysAgo(30),
    to: today(),
    clientQuery: '',
    employeeId: null,
  };
}

export const BookingHistoryStore = signalStore(
  withState<HistoryState>({
    items: [],
    page: 0,
    size: PAGE_SIZE,
    hasMore: false,
    filters: initialFilters(),
  }),
  withRequestStatus(),
  withComputed((store) => ({
    emptyState: computed(() => store.items().length === 0),
    groupedByDay: computed(() => groupByDay(store.items())),
  })),
  withMethods((store, bookings = inject(BookingsService), salonClients = inject(SalonClientService)) => ({
    updateFilters(partial: Partial<HistoryFilters>): void {
      const nextFilters = { ...store.filters(), ...partial };
      patchState(store, { filters: nextFilters, items: [], page: 0, hasMore: false });
      loadPage(store, bookings, 0);
    },
    loadNextPage(): void {
      if (!store.hasMore() || store.isPending()) return;
      loadPage(store, bookings, store.page() + 1);
    },
    searchClient: rxMethod<string>(
      pipe(
        debounceTime(300),
        distinctUntilChanged(),
        tap((query) => {
          patchState(store, {
            filters: { ...store.filters(), clientQuery: query },
            items: [],
            page: 0,
            hasMore: false,
          });
        }),
        switchMap(() => {
          loadPage(store, bookings, 0);
          return [];
        }),
      )
    ),
  })),
  withHooks({
    onInit(store) {
      (store as any).updateFilters({});
    },
  }),
);

function loadPage(
  store: any,
  bookings: BookingsService,
  pageNum: number,
): void {
  const f = store.filters() as HistoryFilters;
  const apiFilters: BookingFilters = {
    from: f.from,
    to: f.to,
    ...(f.employeeId ? { userId: f.employeeId } : {}),
  };

  // Single-status optimization: send to backend only if exactly one selected
  const onlyOneStatus = f.statuses.length === 1;
  if (onlyOneStatus) {
    apiFilters.status = f.statuses[0];
  }

  patchState(store, setPending());

  bookings
    .listDetailed(apiFilters, { page: pageNum, size: PAGE_SIZE, sort: 'appointmentDate,desc' })
    .subscribe({
      next: (res) => {
        let content = res.content;
        // Client-side status filter when 2-3 statuses selected
        if (f.statuses.length >= 2 && f.statuses.length <= 3) {
          content = content.filter((b) => f.statuses.includes(b.status));
        }
        // Client-side name filter if clientQuery present and didn't resolve to userId
        if (f.clientQuery && f.clientQuery.length > 0) {
          const q = f.clientQuery.toLowerCase();
          content = content.filter((b) =>
            (b.salonClientName ?? b.user.name).toLowerCase().includes(q),
          );
        }
        const newItems = pageNum === 0 ? content : [...store.items(), ...content];
        patchState(store, {
          items: newItems,
          page: pageNum,
          hasMore: !res.last,
        }, setFulfilled());
      },
      error: (err) => patchState(store, setError(err?.message ?? 'error')),
    });
}

interface DayGroup {
  date: string; // YYYY-MM-DD
  label: string; // Display label (jj/MM/yyyy)
  items: CareBookingDetailed[];
}

function groupByDay(items: CareBookingDetailed[]): DayGroup[] {
  const groups = new Map<string, CareBookingDetailed[]>();
  for (const b of items) {
    const arr = groups.get(b.appointmentDate) ?? [];
    arr.push(b);
    groups.set(b.appointmentDate, arr);
  }
  return Array.from(groups.entries())
    .sort((a, b) => (a[0] < b[0] ? 1 : -1))
    .map(([date, dayItems]) => ({
      date,
      label: formatDay(date),
      items: dayItems.sort((a, b) => (a.appointmentTime < b.appointmentTime ? 1 : -1)),
    }));
}

function formatDay(ymd: string): string {
  const [y, m, d] = ymd.split('-');
  return `${d}/${m}/${y}`;
}
```

- [ ] **Step 2: Verify the `withRequestStatus` import path**

Run: `grep -rn "withRequestStatus" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app --include="*.ts" | head -5`

If the path in the grep result differs from `../../../shared/features/request-status/request-status.feature`, update the import in the new store file accordingly.

- [ ] **Step 3: Run the tests**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --include='**/booking-history.store.spec.ts' --watch=false`

Expected: 7/7 PASS.

If any fail, fix the store implementation (not the tests) until they pass.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/booking-history.store.ts
git commit -m "feat: implement BookingHistoryStore"
```

---

## Task 6: Implement `PeriodFilterSheetComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/pro-booking-history/filters/period-filter-sheet.component.ts`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/app/pages/pro/pro-booking-history/filters/period-filter-sheet.component.ts
import { Component, inject, signal } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatRadioModule } from '@angular/material/radio';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideNativeDateAdapter } from '@angular/material/core';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';

export type PeriodPreset = '30days' | '3months' | '6months' | 'custom';

export interface PeriodResult {
  preset: PeriodPreset;
  from: string;
  to: string;
}

@Component({
  selector: 'app-period-filter-sheet',
  standalone: true,
  imports: [
    MatRadioModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    FormsModule,
    TranslocoPipe,
    SheetHandleComponent,
  ],
  providers: [provideNativeDateAdapter()],
  template: `
    <app-sheet-handle />
    <div class="sheet-wrap">
      <h3>{{ 'pro.history.filter.period.custom' | transloco }}</h3>
      <mat-radio-group [(ngModel)]="preset" (ngModelChange)="onPresetChange($event)">
        <mat-radio-button value="30days">{{ 'pro.history.filter.period.30days' | transloco }}</mat-radio-button>
        <mat-radio-button value="3months">{{ 'pro.history.filter.period.3months' | transloco }}</mat-radio-button>
        <mat-radio-button value="6months">{{ 'pro.history.filter.period.6months' | transloco }}</mat-radio-button>
        <mat-radio-button value="custom">{{ 'pro.history.filter.period.custom' | transloco }}</mat-radio-button>
      </mat-radio-group>

      @if (preset() === 'custom') {
        <div class="range">
          <mat-form-field>
            <mat-label>{{ 'pro.history.filter.period.from' | transloco }}</mat-label>
            <input matInput [matDatepicker]="fromPicker" [(ngModel)]="fromDate" />
            <mat-datepicker-toggle matIconSuffix [for]="fromPicker" />
            <mat-datepicker #fromPicker />
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'pro.history.filter.period.to' | transloco }}</mat-label>
            <input matInput [matDatepicker]="toPicker" [(ngModel)]="toDate" [min]="fromDate()" />
            <mat-datepicker-toggle matIconSuffix [for]="toPicker" />
            <mat-datepicker #toPicker />
          </mat-form-field>
        </div>
      }

      <button class="apply" (click)="apply()">{{ 'common.apply' | transloco }}</button>
    </div>
  `,
  styles: [`
    .sheet-wrap { padding: 16px; display: flex; flex-direction: column; gap: 14px; }
    h3 { margin: 0; font-size: 16px; color: #333; }
    mat-radio-group { display: flex; flex-direction: column; gap: 8px; }
    .range { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .apply {
      margin-top: 8px; padding: 12px; border: none; border-radius: 10px;
      background: #c06; color: white; font-weight: 600; font-size: 14px;
      cursor: pointer;
    }
  `],
})
export class PeriodFilterSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<PeriodFilterSheetComponent>);

  readonly preset = signal<PeriodPreset>('30days');
  readonly fromDate = signal<Date | null>(null);
  readonly toDate = signal<Date | null>(new Date());

  onPresetChange(value: PeriodPreset): void {
    this.preset.set(value);
  }

  apply(): void {
    const today = new Date();
    const todayStr = toYMD(today);
    let from = todayStr;
    let to = todayStr;

    switch (this.preset()) {
      case '30days':
        from = toYMD(addDays(today, -30));
        break;
      case '3months':
        from = toYMD(addDays(today, -90));
        break;
      case '6months':
        from = toYMD(addDays(today, -180));
        break;
      case 'custom': {
        const f = this.fromDate();
        const t = this.toDate();
        if (!f || !t) return;
        from = toYMD(f);
        to = toYMD(t);
        break;
      }
    }

    this.dialogRef.close({ preset: this.preset(), from, to } as PeriodResult);
  }
}

function addDays(d: Date, days: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + days);
  return r;
}

function toYMD(d: Date): string {
  const y = d.getFullYear();
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${y}-${m}-${day}`;
}
```

- [ ] **Step 2: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -20`

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/filters/period-filter-sheet.component.ts
git commit -m "feat: add PeriodFilterSheetComponent"
```

---

## Task 7: Implement `StatusFilterSheetComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/pro-booking-history/filters/status-filter-sheet.component.ts`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/app/pages/pro/pro-booking-history/filters/status-filter-sheet.component.ts
import { Component, inject, signal } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslocoPipe } from '@jsverse/transloco';
import { CareBookingStatus } from '../../../../features/bookings/models/bookings.model';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';

@Component({
  selector: 'app-status-filter-sheet',
  standalone: true,
  imports: [MatCheckboxModule, TranslocoPipe, SheetHandleComponent],
  template: `
    <app-sheet-handle />
    <div class="sheet-wrap">
      <h3>{{ 'pro.history.filter.status.all' | transloco }}</h3>
      <div class="list">
        @for (s of allStatuses; track s) {
          <mat-checkbox
            [checked]="selected().includes(s)"
            (change)="toggle(s)">
            {{ 'bookings.status.' + s | transloco }}
          </mat-checkbox>
        }
      </div>
      <button class="apply" (click)="apply()">{{ 'common.apply' | transloco }}</button>
    </div>
  `,
  styles: [`
    .sheet-wrap { padding: 16px; display: flex; flex-direction: column; gap: 14px; }
    h3 { margin: 0; font-size: 16px; color: #333; }
    .list { display: flex; flex-direction: column; gap: 10px; }
    .apply {
      margin-top: 8px; padding: 12px; border: none; border-radius: 10px;
      background: #c06; color: white; font-weight: 600; font-size: 14px;
      cursor: pointer;
    }
  `],
})
export class StatusFilterSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<StatusFilterSheetComponent>);
  private readonly data = inject<{ selected: CareBookingStatus[] }>(MAT_DIALOG_DATA, { optional: true }) ?? { selected: [] };

  readonly allStatuses: CareBookingStatus[] = [
    CareBookingStatus.CONFIRMED,
    CareBookingStatus.PENDING,
    CareBookingStatus.CANCELLED,
    CareBookingStatus.NO_SHOW,
  ];

  readonly selected = signal<CareBookingStatus[]>([...(this.data.selected ?? [])]);

  toggle(s: CareBookingStatus): void {
    const cur = this.selected();
    this.selected.set(cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]);
  }

  apply(): void {
    this.dialogRef.close(this.selected());
  }
}
```

- [ ] **Step 2: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -20`

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/filters/status-filter-sheet.component.ts
git commit -m "feat: add StatusFilterSheetComponent"
```

---

## Task 8: Implement `EmployeeFilterSheetComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/pro-booking-history/filters/employee-filter-sheet.component.ts`

- [ ] **Step 1: Verify the EmployeesStore selector shape**

Run: `grep -rn "class EmployeesStore\|withState.*employees" /Users/Gustavo.alves/Documents/personal/portfolio/frontend/src/app/features/employees --include="*.ts" | head -5`

If the store exposes a method like `employees()` returning `{id, name}[]`, use that. Otherwise, look at `EmployeesStore` and adapt accordingly. The component below assumes `store.employees()` returns objects with `id` (number) and `name` (string).

- [ ] **Step 2: Create the component**

```typescript
// frontend/src/app/pages/pro/pro-booking-history/filters/employee-filter-sheet.component.ts
import { Component, inject, signal } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatRadioModule } from '@angular/material/radio';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';
import { EmployeesStore } from '../../../../features/employees/store/employees.store';

@Component({
  selector: 'app-employee-filter-sheet',
  standalone: true,
  imports: [MatRadioModule, FormsModule, TranslocoPipe, SheetHandleComponent],
  providers: [EmployeesStore],
  template: `
    <app-sheet-handle />
    <div class="sheet-wrap">
      <h3>{{ 'pro.history.filter.employee.all' | transloco }}</h3>
      <mat-radio-group [(ngModel)]="value">
        <mat-radio-button [value]="null">{{ 'pro.history.filter.employee.all' | transloco }}</mat-radio-button>
        @for (e of employeesStore.employees(); track e.id) {
          <mat-radio-button [value]="e.id">{{ e.name }}</mat-radio-button>
        }
      </mat-radio-group>
      <button class="apply" (click)="apply()">{{ 'common.apply' | transloco }}</button>
    </div>
  `,
  styles: [`
    .sheet-wrap { padding: 16px; display: flex; flex-direction: column; gap: 14px; max-height: 60vh; overflow-y: auto; }
    h3 { margin: 0; font-size: 16px; color: #333; }
    mat-radio-group { display: flex; flex-direction: column; gap: 8px; }
    .apply {
      margin-top: 8px; padding: 12px; border: none; border-radius: 10px;
      background: #c06; color: white; font-weight: 600; font-size: 14px;
      cursor: pointer;
    }
  `],
})
export class EmployeeFilterSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<EmployeeFilterSheetComponent>);
  protected readonly employeesStore = inject(EmployeesStore);
  private readonly data = inject<{ selected: number | null }>(MAT_DIALOG_DATA, { optional: true }) ?? { selected: null };

  value: number | null = this.data.selected;

  apply(): void {
    this.dialogRef.close(this.value);
  }
}
```

If the Step 1 grep showed that `EmployeesStore` exposes something different from `employees()`, adapt the `@for` binding to use the correct signal name (e.g., `store.list()`, `store.items()`).

- [ ] **Step 3: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -20`

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/filters/employee-filter-sheet.component.ts
git commit -m "feat: add EmployeeFilterSheetComponent"
```

---

## Task 9: Implement `ProBookingHistoryComponent`

**Files:**
- Create: `frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts
import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, afterNextRender, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { BookingHistoryStore } from './booking-history.store';
import { BookingCardComponent } from '../../../features/bookings/components/booking-card/booking-card.component';
import { CareBookingDetailed, CareBookingStatus } from '../../../features/bookings/models/bookings.model';
import { bottomSheetConfig } from '../../../shared/uis/sheet-handle/bottom-sheet.config';
import { PeriodFilterSheetComponent, PeriodResult } from './filters/period-filter-sheet.component';
import { StatusFilterSheetComponent } from './filters/status-filter-sheet.component';
import { EmployeeFilterSheetComponent } from './filters/employee-filter-sheet.component';

@Component({
  selector: 'app-pro-booking-history',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
    BookingCardComponent,
  ],
  providers: [BookingHistoryStore],
  template: `
    <div class="history-page">
      <header class="page-header">
        <button class="back" (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
        <h1>{{ 'pro.history.title' | transloco }}</h1>
      </header>

      <div class="search-box">
        <mat-icon>search</mat-icon>
        <input
          type="text"
          [placeholder]="'pro.history.search' | transloco"
          [ngModel]="store.filters().clientQuery"
          (ngModelChange)="store.searchClient($event)"
        />
      </div>

      <div class="filters-row">
        <button class="chip" (click)="openPeriod()">{{ periodLabel() }}</button>
        <button class="chip" (click)="openStatus()">{{ statusLabel() }}</button>
        <button class="chip" (click)="openEmployee()">{{ employeeLabel() }}</button>
      </div>

      @if (store.isPending() && store.items().length === 0) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      } @else if (store.emptyState()) {
        <div class="empty">
          <mat-icon>history</mat-icon>
          <p>{{ 'pro.history.empty' | transloco }}</p>
        </div>
      } @else {
        @for (group of store.groupedByDay(); track group.date) {
          <div class="day-label">{{ group.label }}</div>
          @for (b of group.items; track b.id) {
            <app-booking-card [booking]="b" (cardClick)="openClient($event)" />
          }
        }
        <div #sentinel class="sentinel">
          @if (store.isPending()) {
            <mat-spinner diameter="20" />
          } @else if (!store.hasMore()) {
            <span class="end">{{ 'pro.history.endOfList' | transloco }}</span>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .history-page {
      padding: 12px 14px 40px;
      max-width: 720px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-height: 100vh;
    }
    .page-header {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 0;
    }
    .page-header h1 {
      font-size: 18px; font-weight: 600; margin: 0; color: #333;
    }
    .back {
      background: none; border: none; color: #666; cursor: pointer;
      padding: 4px; display: flex; align-items: center;
    }
    .search-box {
      background: white; border-radius: 10px;
      padding: 8px 12px; display: flex; align-items: center; gap: 8px;
      box-shadow: 0 1px 2px rgba(0,0,0,0.04);
    }
    .search-box input {
      border: none; outline: none; font-size: 14px; flex: 1;
      background: transparent;
    }
    .search-box mat-icon { color: #999; font-size: 18px; width: 18px; height: 18px; }
    .filters-row {
      display: flex; gap: 6px; overflow-x: auto;
      padding: 4px 0 8px;
    }
    .chip {
      background: white;
      border: 1px solid #e5e5e5;
      padding: 6px 12px;
      border-radius: 16px;
      font-size: 12px;
      color: #555;
      font-weight: 500;
      white-space: nowrap;
      cursor: pointer;
    }
    .chip:hover { border-color: #c06; color: #c06; }
    .day-label {
      font-size: 11px; font-weight: 700; color: #666;
      text-transform: uppercase; letter-spacing: 0.5px;
      margin: 10px 2px 4px;
    }
    .empty {
      text-align: center; padding: 40px 0; color: #999;
      display: flex; flex-direction: column; align-items: center; gap: 10px;
    }
    .empty mat-icon { font-size: 48px; width: 48px; height: 48px; }
    .loading { display: flex; justify-content: center; padding: 30px 0; }
    .sentinel {
      min-height: 30px;
      display: flex; justify-content: center; align-items: center;
      padding: 16px 0;
    }
    .sentinel .end { font-size: 12px; color: #999; font-style: italic; }
  `],
})
export class ProBookingHistoryComponent implements AfterViewInit, OnDestroy {
  protected readonly store = inject(BookingHistoryStore);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  @ViewChild('sentinel') sentinel?: ElementRef<HTMLElement>;
  private observer?: IntersectionObserver;

  constructor() {
    afterNextRender(() => this.setupObserver());
  }

  ngAfterViewInit(): void {
    this.setupObserver();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private setupObserver(): void {
    if (typeof IntersectionObserver === 'undefined' || !this.sentinel) return;
    this.observer?.disconnect();
    this.observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) {
        this.store.loadNextPage();
      }
    }, { rootMargin: '200px' });
    this.observer.observe(this.sentinel.nativeElement);
  }

  protected periodLabel(): string {
    const f = this.store.filters();
    return `${formatDM(f.from)} → ${formatDM(f.to)}`;
  }

  protected statusLabel(): string {
    const n = this.store.filters().statuses.length;
    if (n === 4 || n === 0) return 'Statut: Tous';
    return `Statut: ${n}`;
  }

  protected employeeLabel(): string {
    return this.store.filters().employeeId === null ? 'Employé: Tous' : 'Employé: 1';
  }

  protected openClient(b: CareBookingDetailed): void {
    this.router.navigate(['/pro/clients', b.user.id]);
  }

  protected goBack(): void {
    this.router.navigate(['/pro/settings']);
  }

  protected openPeriod(): void {
    const ref = this.dialog.open<PeriodFilterSheetComponent, unknown, PeriodResult>(
      PeriodFilterSheetComponent,
      bottomSheetConfig(),
    );
    ref.afterClosed().subscribe((res) => {
      if (!res) return;
      this.store.updateFilters({ from: res.from, to: res.to });
    });
  }

  protected openStatus(): void {
    const ref = this.dialog.open<StatusFilterSheetComponent, unknown, CareBookingStatus[]>(
      StatusFilterSheetComponent,
      bottomSheetConfig({ data: { selected: this.store.filters().statuses } }),
    );
    ref.afterClosed().subscribe((selected) => {
      if (!selected) return;
      this.store.updateFilters({ statuses: selected });
    });
  }

  protected openEmployee(): void {
    const ref = this.dialog.open<EmployeeFilterSheetComponent, unknown, number | null>(
      EmployeeFilterSheetComponent,
      bottomSheetConfig({ data: { selected: this.store.filters().employeeId } }),
    );
    ref.afterClosed().subscribe((value) => {
      if (value === undefined) return;
      this.store.updateFilters({ employeeId: value });
    });
  }
}

function formatDM(ymd: string): string {
  const [, m, d] = ymd.split('-');
  return `${d}/${m}`;
}
```

- [ ] **Step 2: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -20`

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/pro-booking-history/pro-booking-history.component.ts
git commit -m "feat: add ProBookingHistoryComponent"
```

---

## Task 10: Wire route and settings card

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/pages/pro/pro-settings.component.ts`

- [ ] **Step 1: Add the route**

Open `frontend/src/app/app.routes.ts` and locate the existing pro `settings` route (path `'settings'` under the pro block, around line 83). Add a sibling:

```typescript
{
  path: 'settings/history',
  loadComponent: () =>
    import('./pages/pro/pro-booking-history/pro-booking-history.component')
      .then((m) => m.ProBookingHistoryComponent),
},
```

Place it immediately after the existing `settings` route entry.

- [ ] **Step 2: Add the navigation card in settings**

Open `frontend/src/app/pages/pro/pro-settings.component.ts`. Locate the template area where existing settings cards live. Add a new card at the top or bottom of the list:

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

Ensure `RouterLink` is in the component's `imports` array (likely already present if other cards use `routerLink`). If not, add:

```typescript
import { RouterLink } from '@angular/router';
```
and add `RouterLink` to the `imports` array.

If the existing cards use a different CSS class name than `.settings-card`, reuse that class so the new card inherits the same layout. Read the existing markup before adding.

- [ ] **Step 3: Build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm run build 2>&1 | tail -20`

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts frontend/src/app/pages/pro/pro-settings.component.ts
git commit -m "feat: wire pro booking history route and settings card"
```

---

## Task 11: Manual QA

**Files:** none.

- [ ] **Step 1: Start the dev server**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm start`

Wait for `Application bundle generation complete`.

- [ ] **Step 2: Open DevTools responsive mode at iPhone SE**

Open `http://localhost:4200`, `Cmd+Shift+M`, pick iPhone SE (375×667). Log in as a pro account.

- [ ] **Step 3: Navigate to `/pro/settings`**

Verify the new "Historique des bookings" card appears. Tap it.

- [ ] **Step 4: Verify the history page**

- Page title renders: "Historique des bookings"
- Search box, 3 filter chips visible
- Either a list grouped by day (if data exists in the last 30 days), or the empty state
- Each booking card renders correctly via the shared `BookingCardComponent`
- Tap any booking → navigates to `/pro/clients/{id}`

- [ ] **Step 5: Verify each filter**

- Tap "période" chip → sheet opens with 4 radio presets + custom fields if custom chosen. Apply → list refreshes.
- Tap "statut" chip → 4 checkboxes, selected state respects current filter. Uncheck 2 → apply → list filters client-side.
- Tap "employé" chip → list of employees + "Tous". Apply → list refreshes.

- [ ] **Step 6: Verify infinite scroll**

Make sure the data set exceeds 20 items for the current filters (adjust filter range if needed). Scroll to the bottom → spinner visible → next batch loads → sentinel shows "end of list" when there is no more data.

- [ ] **Step 7: Verify desktop layout**

Disable device toolbar (`Cmd+Shift+M`). Confirm the page is usable on ≥768px (chips, cards, filters all legible, no overflow).

- [ ] **Step 8: No commit needed if all checks pass**

If a bug is found, fix it in a new commit referencing the broken behavior.

---

## Self-Review

**Spec coverage:**
- ✅ Route `/pro/settings/history` → Task 10
- ✅ Navigation card in settings → Task 10
- ✅ `BookingHistoryStore` with all decisions → Tasks 4-5
- ✅ 4 filters (period, status, employee, client search) → Tasks 6-8 + Task 9
- ✅ Infinite scroll via IntersectionObserver → Task 9
- ✅ Tap row → navigate to client profile → Task 9
- ✅ Shared `BookingCardComponent` + refactor of `pro-bookings` → Tasks 1-2
- ✅ i18n keys → Task 3
- ✅ Unit tests for store and card → Tasks 1, 4

**Placeholder check:** None.

**Type consistency:** `HistoryFilters`, `BookingHistoryStore`, `ProBookingHistoryComponent`, `CareBookingStatus`, `CareBookingDetailed`, `BookingCardComponent`, `bottomSheetConfig` — all names consistent across tasks.

**Backend constraint:** Handled via client-side logic documented at the top of the plan and asserted in tests (Task 4 spec items for single/multi status).
