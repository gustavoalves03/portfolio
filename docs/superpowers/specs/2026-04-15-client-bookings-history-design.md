# Client Bookings History on Client Detail Page

**Date:** 2026-04-15
**Status:** Approved

## Overview

Add a "Rendez-vous" section to the client detail page (`/pro/clients/:userId`) showing the client's booking history with tabbed navigation (upcoming/past), today's booking highlighted, and a No-Show button with confirmation dialog for past confirmed bookings.

## Design Decision

**Chosen approach:** Option B — Onglets "A venir / Passes"

Segmented toggle separating upcoming and past bookings. Today's booking shown as a highlighted card. Compact layout that doesn't overwhelm the existing client detail page.

## Architecture

### New Files

- `frontend/src/app/features/tracking/components/client-bookings/client-bookings.component.ts` — Main component with tabbed booking list
- `frontend/src/app/features/tracking/components/client-bookings/client-bookings.store.ts` — NgRx SignalStore for booking data and actions
- `frontend/src/app/features/tracking/components/client-bookings/no-show-confirm-dialog.component.ts` — Material confirmation dialog

### Modified Files

- `frontend/src/app/pages/pro/pro-client-detail.component.ts` — Insert `ClientBookingsComponent` between header and visits
- `frontend/public/i18n/fr.json` — French translations
- `frontend/public/i18n/en.json` — English translations

### No Backend Changes

- Reuse existing `GET /api/bookings?userId={userId}` returning `CareBookingDetailed[]`
- Reuse existing `PUT /api/bookings/{id}` with `status: NO_SHOW` (backend already enforces NO_SHOW for past bookings)

## Component Design

### `ClientBookingsStore` (SignalStore)

```
State:
  - bookings: CareBookingDetailed[]
  - activeTab: 'upcoming' | 'past'

Computed:
  - todayBookings: bookings where appointmentDate === today
  - upcomingBookings: bookings where appointmentDate > today, sorted ascending
  - pastBookings: bookings where appointmentDate < today, sorted descending
  - upcomingCount: todayBookings.length + upcomingBookings.length
  - pastCount: pastBookings.length

Methods:
  - loadBookings(userId: number): rxMethod — fetches bookings via BookingsService.list({userId})
  - markNoShow(bookingId: number): calls PUT to update status to NO_SHOW, then reloads bookings
  - setActiveTab(tab): patches activeTab
```

Uses `withRequestStatus()` for loading states. Provided at `ClientBookingsComponent` level.

### `ClientBookingsComponent`

- **Input:** `userId: number`
- **Layout:**
  1. Section header "Rendez-vous"
  2. `mat-button-toggle-group` with two toggles: "A venir (N)" / "Passes (N)"
  3. Today's booking card — highlighted with pink border (`#c06`) and "Aujourd'hui" badge, shown at top of "A venir" tab
  4. Booking list — compact cards showing: care name, date, time, employee name, status badge
  5. No-Show button — red button on each past booking with status CONFIRMED
- **Empty state:** message when no bookings in active tab

### `NoShowConfirmDialog`

- **Data input:** `{careName: string, appointmentDate: string}`
- **Content:** Title, message mentioning the care name and date, two buttons
- **Actions:** "Annuler" (closes dialog, returns false) / "Confirmer No-Show" (closes dialog, returns true)

### Status Badge Colors

- CONFIRMED: green (`#52b788`)
- PENDING: orange (`#fb923c`)
- CANCELLED: red (`#ef5350`)
- NO_SHOW: gray (`#999`)

### Today's Booking Highlight

- White background with pink border (`1.5px solid #c06`)
- Small "Aujourd'hui" badge: pink background (`#c06`), white text, positioned top-left
- Shown at the top of the "A venir" tab

## Integration

`ClientBookingsComponent` is inserted in `pro-client-detail.component.ts` template between `<app-client-header>` and `<app-client-visits>`:

```html
<app-client-header ... />
<app-client-bookings [userId]="userId" />
<app-client-visits ... />
```

## i18n Keys

All user-facing text uses Transloco. Keys added to both `fr.json` and `en.json`:

```
bookings.section.title          — "Rendez-vous" / "Appointments"
bookings.tabs.upcoming          — "A venir" / "Upcoming"
bookings.tabs.past              — "Passes" / "Past"
bookings.today                  — "Aujourd'hui" / "Today"
bookings.noBookings             — "Aucun rendez-vous" / "No appointments"
bookings.noShow.title           — "Confirmer No-Show" / "Confirm No-Show"
bookings.noShow.message         — "Marquer {careName} du {date} comme no-show ?" / "Mark {careName} on {date} as no-show?"
bookings.noShow.cancel          — "Annuler" / "Cancel"
bookings.noShow.submit          — "Confirmer No-Show" / "Confirm No-Show"
bookings.status.confirmed       — "Confirme" / "Confirmed"
bookings.status.pending         — "En attente" / "Pending"
bookings.status.cancelled       — "Annule" / "Cancelled"
bookings.status.noShow          — "No-Show" / "No-Show"
```
