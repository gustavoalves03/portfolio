# Client Bookings History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tabbed bookings section (upcoming/past) with today-highlight and no-show button to the client detail page.

**Architecture:** New `ClientBookingsComponent` with its own `ClientBookingsStore` (NgRx SignalStore), inserted between client header and visits on the pro client detail page. A `NoShowConfirmDialog` (Material Dialog) confirms the action. No backend changes — reuses existing `GET /api/bookings/detailed?userId=X` and `PUT /api/bookings/{id}`.

**Tech Stack:** Angular 20 (standalone, zoneless, signals), NgRx SignalStore, Angular Material (button-toggle, dialog), Transloco i18n

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `frontend/src/app/features/tracking/components/client-bookings/client-bookings.store.ts` | SignalStore: load bookings, computed splits (today/upcoming/past), markNoShow |
| Create | `frontend/src/app/features/tracking/components/client-bookings/client-bookings.component.ts` | Tabbed UI: toggle, today card, booking list, no-show button |
| Create | `frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts` | Material confirmation dialog for no-show action |
| Modify | `frontend/src/app/pages/pro/pro-client-detail.component.ts` | Insert `<app-client-bookings>` between header and visits |
| Modify | `frontend/public/i18n/fr.json` | Add `tracking.bookings.*` keys |
| Modify | `frontend/public/i18n/en.json` | Add `tracking.bookings.*` keys |

---

### Task 1: Add i18n Translation Keys

**Files:**
- Modify: `frontend/public/i18n/fr.json` (inside `"tracking"` object)
- Modify: `frontend/public/i18n/en.json` (inside `"tracking"` object)

- [ ] **Step 1: Add French translations**

Add the following keys inside the existing `"tracking"` object in `fr.json`, after the `"reminder"` key (line 777):

```json
"bookings": {
  "title": "Rendez-vous",
  "upcoming": "À venir",
  "past": "Passés",
  "today": "Aujourd'hui",
  "empty": "Aucun rendez-vous",
  "noShow": {
    "title": "Confirmer No-Show",
    "message": "Marquer « {{careName}} » du {{date}} comme absent(e) ?",
    "cancel": "Annuler",
    "submit": "Confirmer No-Show",
    "success": "Rendez-vous marqué comme no-show"
  },
  "status": {
    "CONFIRMED": "Confirmé",
    "PENDING": "En attente",
    "CANCELLED": "Annulé",
    "NO_SHOW": "Absent"
  }
}
```

- [ ] **Step 2: Add English translations**

Add the following keys inside the existing `"tracking"` object in `en.json`, after the `"reminder"` key:

```json
"bookings": {
  "title": "Appointments",
  "upcoming": "Upcoming",
  "past": "Past",
  "today": "Today",
  "empty": "No appointments",
  "noShow": {
    "title": "Confirm No-Show",
    "message": "Mark \"{{careName}}\" on {{date}} as no-show?",
    "cancel": "Cancel",
    "submit": "Confirm No-Show",
    "success": "Appointment marked as no-show"
  },
  "status": {
    "CONFIRMED": "Confirmed",
    "PENDING": "Pending",
    "CANCELLED": "Cancelled",
    "NO_SHOW": "No-Show"
  }
}
```

- [ ] **Step 3: Verify JSON is valid**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio && node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/fr.json','utf8')); JSON.parse(require('fs').readFileSync('frontend/public/i18n/en.json','utf8')); console.log('OK')"`

Expected: `OK`

- [ ] **Step 4: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add i18n keys for client bookings section"
```

---

### Task 2: Create ClientBookingsStore

**Files:**
- Create: `frontend/src/app/features/tracking/components/client-bookings/client-bookings.store.ts`

- [ ] **Step 1: Create the store file**

```typescript
import { computed, inject } from '@angular/core';
import {
  patchState,
  signalStore,
  withComputed,
  withMethods,
  withState,
} from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, pipe, switchMap, tap } from 'rxjs';
import { BookingsService } from '../../../bookings/services/bookings.service';
import {
  CareBookingDetailed,
  CareBookingStatus,
} from '../../../bookings/models/bookings.model';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../../../shared/features/request.status.feature';

type ActiveTab = 'upcoming' | 'past';

type ClientBookingsState = {
  bookings: CareBookingDetailed[];
  activeTab: ActiveTab;
};

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

export const ClientBookingsStore = signalStore(
  withState<ClientBookingsState>({
    bookings: [],
    activeTab: 'upcoming',
  }),
  withRequestStatus(),
  withComputed((store) => {
    const today = computed(() => todayStr());

    const todayBookings = computed(() =>
      store
        .bookings()
        .filter((b) => b.appointmentDate === today())
        .sort((a, b) => a.appointmentTime.localeCompare(b.appointmentTime))
    );

    const upcomingBookings = computed(() =>
      store
        .bookings()
        .filter((b) => b.appointmentDate > today())
        .sort(
          (a, b) =>
            a.appointmentDate.localeCompare(b.appointmentDate) ||
            a.appointmentTime.localeCompare(b.appointmentTime)
        )
    );

    const pastBookings = computed(() =>
      store
        .bookings()
        .filter((b) => b.appointmentDate < today())
        .sort(
          (a, b) =>
            b.appointmentDate.localeCompare(a.appointmentDate) ||
            b.appointmentTime.localeCompare(a.appointmentTime)
        )
    );

    return {
      todayBookings,
      upcomingBookings,
      pastBookings,
      upcomingCount: computed(
        () => todayBookings().length + upcomingBookings().length
      ),
      pastCount: computed(() => pastBookings().length),
    };
  }),
  withMethods(
    (store, bookingsService = inject(BookingsService)) => {
      let currentUserId = 0;

      return {
        loadBookings: rxMethod<number>(
          pipe(
            tap((userId) => {
              currentUserId = userId;
              patchState(store, setPending());
            }),
            switchMap((userId) =>
              bookingsService
                .listDetailed({ userId }, { size: 100, sort: 'appointmentDate,desc' })
                .pipe(
                  tap((page) =>
                    patchState(store, { bookings: page.content }, setFulfilled())
                  ),
                  catchError(() => {
                    patchState(store, setError('Error loading bookings'));
                    return EMPTY;
                  })
                )
            )
          )
        ),

        markNoShow: rxMethod<CareBookingDetailed>(
          pipe(
            tap(() => patchState(store, setPending())),
            switchMap((booking) =>
              bookingsService
                .update(booking.id, {
                  userId: booking.user.id,
                  careId: booking.care.id,
                  quantity: booking.quantity,
                  appointmentDate: booking.appointmentDate,
                  appointmentTime: booking.appointmentTime,
                  status: CareBookingStatus.NO_SHOW,
                  salonClientId: booking.salonClientId ?? undefined,
                })
                .pipe(
                  switchMap(() =>
                    bookingsService
                      .listDetailed(
                        { userId: currentUserId },
                        { size: 100, sort: 'appointmentDate,desc' }
                      )
                      .pipe(
                        tap((page) =>
                          patchState(
                            store,
                            { bookings: page.content },
                            setFulfilled()
                          )
                        )
                      )
                  ),
                  catchError(() => {
                    patchState(store, setError('Error marking no-show'));
                    return EMPTY;
                  })
                )
            )
          )
        ),

        setActiveTab(tab: ActiveTab): void {
          patchState(store, { activeTab: tab });
        },
      };
    }
  )
);
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -5`

Expected: Build succeeds (the store is not imported anywhere yet, so no errors)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/tracking/components/client-bookings/client-bookings.store.ts
git commit -m "feat: create ClientBookingsStore for client detail page"
```

---

### Task 3: Create NoShowConfirmDialog

**Files:**
- Create: `frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts`

- [ ] **Step 1: Create the dialog component**

```typescript
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

export interface NoShowConfirmData {
  careName: string;
  appointmentDate: string;
}

@Component({
  selector: 'app-no-show-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ 'tracking.bookings.noShow.title' | transloco }}</h2>
    <mat-dialog-content>
      <p class="message">
        {{
          'tracking.bookings.noShow.message'
            | transloco: { careName: data.careName, date: data.appointmentDate }
        }}
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close(false)">
        {{ 'tracking.bookings.noShow.cancel' | transloco }}
      </button>
      <button mat-flat-button class="no-show-btn" (click)="dialogRef.close(true)">
        {{ 'tracking.bookings.noShow.submit' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .message {
        font-size: 14px;
        color: #4b5563;
        line-height: 1.5;
      }
      .no-show-btn {
        background: #dc2626 !important;
        color: white !important;
        border-radius: 8px;
      }
    `,
  ],
})
export class NoShowConfirmDialogComponent {
  readonly data = inject<NoShowConfirmData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<NoShowConfirmDialogComponent>);
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts
git commit -m "feat: create NoShowConfirmDialog component"
```

---

### Task 4: Create ClientBookingsComponent

**Files:**
- Create: `frontend/src/app/features/tracking/components/client-bookings/client-bookings.component.ts`

- [ ] **Step 1: Create the component file**

```typescript
import { Component, effect, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ClientBookingsStore } from './client-bookings.store';
import {
  CareBookingDetailed,
  CareBookingStatus,
} from '../../../bookings/models/bookings.model';
import {
  NoShowConfirmDialogComponent,
  NoShowConfirmData,
} from './no-show-confirm-dialog.component';

@Component({
  selector: 'app-client-bookings',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  providers: [ClientBookingsStore],
  template: `
    <div class="section">
      <div class="section-header">
        <span class="section-title">{{ 'tracking.bookings.title' | transloco }}</span>
      </div>

      @if (store.isPending()) {
        <div class="loading">
          <mat-spinner diameter="24"></mat-spinner>
        </div>
      } @else {
        <!-- Tabs -->
        <mat-button-toggle-group
          [value]="store.activeTab()"
          (change)="store.setActiveTab($event.value)"
          class="tabs">
          <mat-button-toggle value="upcoming">
            {{ 'tracking.bookings.upcoming' | transloco }} ({{ store.upcomingCount() }})
          </mat-button-toggle>
          <mat-button-toggle value="past">
            {{ 'tracking.bookings.past' | transloco }} ({{ store.pastCount() }})
          </mat-button-toggle>
        </mat-button-toggle-group>

        @if (store.activeTab() === 'upcoming') {
          <!-- Today's bookings -->
          @for (booking of store.todayBookings(); track booking.id) {
            <div class="booking-card today-card">
              <div class="today-badge">{{ 'tracking.bookings.today' | transloco }}</div>
              <div class="booking-content">
                <div class="booking-info">
                  <div class="care-name">{{ booking.care.name }}</div>
                  <div class="booking-meta">
                    {{ booking.appointmentTime.slice(0, 5) }} · {{ booking.care.duration }} min
                    @if (booking.employeeName) {
                      · {{ booking.employeeName }}
                    }
                  </div>
                </div>
                <span class="status-badge" [attr.data-status]="booking.status">
                  {{ 'tracking.bookings.status.' + booking.status | transloco }}
                </span>
              </div>
            </div>
          }
          <!-- Upcoming bookings -->
          @for (booking of store.upcomingBookings(); track booking.id) {
            <div class="booking-card">
              <div class="booking-content">
                <div class="booking-info">
                  <div class="care-name">{{ booking.care.name }}</div>
                  <div class="booking-meta">
                    {{ booking.appointmentDate | date:'d MMM' }} · {{ booking.appointmentTime.slice(0, 5) }}
                    @if (booking.employeeName) {
                      · {{ booking.employeeName }}
                    }
                  </div>
                </div>
                <span class="status-badge" [attr.data-status]="booking.status">
                  {{ 'tracking.bookings.status.' + booking.status | transloco }}
                </span>
              </div>
            </div>
          }
          @if (store.upcomingCount() === 0) {
            <div class="empty">{{ 'tracking.bookings.empty' | transloco }}</div>
          }
        } @else {
          <!-- Past bookings -->
          @for (booking of store.pastBookings(); track booking.id) {
            <div class="booking-card past-card">
              <div class="booking-content">
                <div class="booking-info">
                  <div class="care-name">{{ booking.care.name }}</div>
                  <div class="booking-meta">
                    {{ booking.appointmentDate | date:'d MMM' }} · {{ booking.appointmentTime.slice(0, 5) }}
                    @if (booking.employeeName) {
                      · {{ booking.employeeName }}
                    }
                  </div>
                </div>
                <div class="past-actions">
                  @if (booking.status === CareBookingStatus.CONFIRMED) {
                    <button class="no-show-btn" (click)="confirmNoShow(booking)">
                      No-Show
                    </button>
                  } @else {
                    <span class="status-badge" [attr.data-status]="booking.status">
                      {{ 'tracking.bookings.status.' + booking.status | transloco }}
                    </span>
                  }
                </div>
              </div>
            </div>
          }
          @if (store.pastCount() === 0) {
            <div class="empty">{{ 'tracking.bookings.empty' | transloco }}</div>
          }
        }
      }
    </div>
  `,
  styles: [
    `
      .section {
        margin-bottom: 12px;
      }
      .section-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
      }
      .section-title {
        font-size: 13px;
        font-weight: 600;
        color: #333;
      }
      .loading {
        display: flex;
        justify-content: center;
        padding: 16px;
      }
      .tabs {
        width: 100%;
        margin-bottom: 10px;
        border-radius: 10px;
        overflow: hidden;

        ::ng-deep .mat-button-toggle {
          flex: 1;
          text-align: center;
          font-size: 12px;
          font-weight: 600;
        }
        ::ng-deep .mat-button-toggle-checked {
          background: #c06;
          color: white;
        }
      }
      .booking-card {
        background: white;
        border-radius: 10px;
        padding: 10px 12px;
        margin-bottom: 6px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
      }
      .today-card {
        border: 1.5px solid #c06;
        position: relative;
        margin-top: 8px;
      }
      .today-badge {
        position: absolute;
        top: -8px;
        left: 12px;
        background: #c06;
        color: white;
        font-size: 9px;
        font-weight: 700;
        padding: 1px 8px;
        border-radius: 4px;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
      .past-card {
        opacity: 0.75;
      }
      .booking-content {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .booking-info {
        flex: 1;
        min-width: 0;
      }
      .care-name {
        font-size: 13px;
        font-weight: 600;
        color: #1a1a2e;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .booking-meta {
        font-size: 11px;
        color: #6b7280;
        margin-top: 2px;
      }
      .status-badge {
        font-size: 10px;
        font-weight: 600;
        padding: 2px 8px;
        border-radius: 6px;
        white-space: nowrap;
        flex-shrink: 0;
      }
      .status-badge[data-status='CONFIRMED'] {
        color: #52b788;
        background: #ecfdf5;
      }
      .status-badge[data-status='PENDING'] {
        color: #fb923c;
        background: #fff7ed;
      }
      .status-badge[data-status='CANCELLED'] {
        color: #ef5350;
        background: #fef2f2;
      }
      .status-badge[data-status='NO_SHOW'] {
        color: #999;
        background: #f3f4f6;
      }
      .past-actions {
        flex-shrink: 0;
      }
      .no-show-btn {
        background: #dc2626;
        color: white;
        border: none;
        border-radius: 6px;
        font-size: 10px;
        font-weight: 600;
        padding: 4px 10px;
        cursor: pointer;
      }
      .no-show-btn:hover {
        background: #b91c1c;
      }
      .empty {
        text-align: center;
        padding: 24px;
        color: #9ca3af;
        font-size: 13px;
      }
    `,
  ],
})
export class ClientBookingsComponent {
  readonly store = inject(ClientBookingsStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  readonly userId = input.required<number>();
  readonly CareBookingStatus = CareBookingStatus;

  constructor() {
    effect(() => {
      const id = this.userId();
      if (id) {
        this.store.loadBookings(id);
      }
    });
  }

  confirmNoShow(booking: CareBookingDetailed): void {
    const dialogRef = this.dialog.open(NoShowConfirmDialogComponent, {
      data: {
        careName: booking.care.name,
        appointmentDate: booking.appointmentDate,
      } satisfies NoShowConfirmData,
      width: '360px',
    });

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.store.markNoShow(booking);
        this.snackBar.open(
          this.transloco.translate('tracking.bookings.noShow.success'),
          undefined,
          { duration: 3000 }
        );
      }
    });
  }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -10`

Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/tracking/components/client-bookings/client-bookings.component.ts
git commit -m "feat: create ClientBookingsComponent with tabbed view and no-show"
```

---

### Task 5: Integrate into Pro Client Detail Page

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-client-detail.component.ts:20-67`

- [ ] **Step 1: Add import and insert component in template**

Add the import at the top of the file (after existing imports):

```typescript
import { ClientBookingsComponent } from '../../features/tracking/components/client-bookings/client-bookings.component';
```

Add `ClientBookingsComponent` to the `imports` array in the `@Component` decorator.

Insert `<app-client-bookings [userId]="userId" />` in the template between `</app-client-header>` (closing of the header, around the allergy alert) and `<app-client-visits`. The exact insertion point is after the `<app-client-header ... />` block and before `<app-client-visits`:

```html
<app-client-header
  [clientName]="history()!.clientName"
  [allergies]="history()!.profile.allergies"
  [visitCount]="history()!.visits.length"
  [createdAt]="history()!.profile.createdAt" />

<app-client-bookings [userId]="userId" />

<app-client-visits
  [visits]="history()!.visits"
  [accessLevel]="'WRITE'"
  [apiBaseUrl]="apiBaseUrl"
  (createVisit)="onCreateVisit()" />
```

Note: `userId` is already a class property (line 109: `private userId = 0;`). It needs to be changed to **non-private** so the template can access it. Change `private userId = 0;` to `userId = 0;`.

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -10`

Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/pro/pro-client-detail.component.ts
git commit -m "feat: integrate client bookings section in pro client detail page"
```

---

### Task 6: Manual Verification

- [ ] **Step 1: Start backend**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn spring-boot:run`

- [ ] **Step 2: Start frontend dev server**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm start`

Or use the Docker dev setup: `docker compose --profile dev up frontend-dev`

- [ ] **Step 3: Test the feature**

1. Navigate to `/pro/bookings` and click on a booking card → should navigate to `/pro/clients/:userId`
2. Verify the new "Rendez-vous" section appears between the client header and visits
3. Check the tab toggle works (À venir / Passés with counts)
4. If there are bookings today, verify they appear with the pink "Aujourd'hui" badge
5. Switch to "Passés" tab — verify past bookings show, and CONFIRMED ones have a red "No-Show" button
6. Click "No-Show" → verify the confirmation dialog appears with care name and date
7. Cancel the dialog → verify nothing changes
8. Confirm the dialog → verify the booking status updates to NO_SHOW and the snackbar shows
9. Switch language (fr/en) → verify all labels are translated
