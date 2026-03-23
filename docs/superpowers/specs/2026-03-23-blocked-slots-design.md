# Story 4.2 — Block & Unblock Specific Time Slots

## Overview

Allow professionals to block specific time slots or full days on their calendar for one-off unavailabilities. New calendar page at `/pro/calendar` with month view, day detail panel, and inline block/unblock actions.

**Approach:** New `BlockedSlot` entity in tenant schema. CRUD endpoints for pro. Public endpoint for client-side slot computation. New calendar page with month grid, day selection, and block management.

## Backend — Entity

New entity in tenant schema:

```
BLOCKED_SLOTS
├── id (PK, IDENTITY)
├── date (DATE)
├── start_time (TIME, nullable — null when full_day)
├── end_time (TIME, nullable — null when full_day)
├── reason (VARCHAR 500, nullable)
├── full_day (BOOLEAN, default false)
```

If `fullDay = true`, `startTime` and `endTime` are null.

## Backend — API

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `GET` | `/api/pro/blocked-slots` | PRO | List future blocked slots |
| `POST` | `/api/pro/blocked-slots` | PRO | Create a block |
| `DELETE` | `/api/pro/blocked-slots/{id}` | PRO | Remove a block (unblock) |
| `GET` | `/api/salon/{slug}/blocked-slots` | Public | Public blocked slots for booking calendar |

### DTOs

```java
record BlockedSlotRequest(
    @NotNull LocalDate date,
    String startTime,     // null if fullDay
    String endTime,       // null if fullDay
    boolean fullDay,
    String reason         // optional
) {}

record BlockedSlotResponse(
    Long id,
    LocalDate date,
    String startTime,
    String endTime,
    boolean fullDay,
    String reason
) {}
```

### Validation

- `date` must not be in the past
- If `fullDay = false`: `startTime` and `endTime` required, `endTime > startTime`
- If `fullDay = true`: `startTime` and `endTime` must be null (service nullifies them)

### Service

- `list()`: returns future blocked slots ordered by date, startTime
- `create(request)`: validates and saves
- `delete(id)`: removes the block

## Frontend — Page `/pro/calendar`

### Route

New lazy-loaded route in `pro` children:
```typescript
{ path: 'calendar', loadComponent: () => import('./features/calendar/calendar.component').then(m => m.CalendarComponent) }
```

### Month View

- Grid 7 columns (Mon→Sun), navigation prev/next month
- Day cells colored:
  - Today: accent color (#c06)
  - Closed days: grayed out (derived from opening hours by dayOfWeek)
  - Days with blocks: pink/red background (#fde8e8)
  - Selected day: highlighted border
- Click on day → shows day detail panel below

### Day Detail Panel

Shows for the selected date:
- Opening hour slots for that day's dayOfWeek (from AvailabilityStore)
- Existing blocked slots for that date (pink, with "Unblock" button)
- Available slots (green)
- Button "+ Block a slot" → inline form:
  - Toggle "Full day" (hides time fields when checked)
  - Start time + End time (when not full day)
  - Reason (optional text input)
  - "Confirm" button

### Service

`CalendarService`:
```
loadBlockedSlots(): Observable<BlockedSlotResponse[]>      → GET /api/pro/blocked-slots
createBlock(req): Observable<BlockedSlotResponse>          → POST /api/pro/blocked-slots
deleteBlock(id): Observable<void>                          → DELETE /api/pro/blocked-slots/{id}
```

### Store

`CalendarStore` with NgRx SignalStore:
- State: `blockedSlots: BlockedSlotResponse[]`, `openingHours: OpeningHourResponse[]`
- Methods: `loadBlockedSlots()`, `loadOpeningHours()`, `createBlock()`, `deleteBlock()`
- Loads both on init

### Calendar Logic

- `getClosedDays()`: derived from opening hours — days with no slots
- `getBlockedDates()`: derived from blocked slots — set of date strings
- `getDayDetail(date)`: combines opening hours for that dayOfWeek with blocked slots for that date

## Navigation

Add to `PRO_NAVIGATION_ROUTES`:
```typescript
{ label: 'Calendrier', path: '/pro/calendar', icon: 'calendar_month', requiresAuth: true, requiredRole: 'PRO' }
```

## Internationalization

Keys in both `fr.json` and `en.json`:

```
pro.calendar.title            — "Mon calendrier" / "My calendar"
pro.calendar.today            — "Aujourd'hui" / "Today"
pro.calendar.blocked          — "Bloqué" / "Blocked"
pro.calendar.available        — "Disponible" / "Available"
pro.calendar.closed           — "Fermé" / "Closed"
pro.calendar.blockSlot        — "Bloquer un créneau" / "Block a slot"
pro.calendar.unblock          — "Débloquer" / "Unblock"
pro.calendar.fullDay          — "Journée entière" / "Full day"
pro.calendar.reason           — "Raison (optionnel)" / "Reason (optional)"
pro.calendar.confirm          — "Confirmer" / "Confirm"
pro.calendar.blockSuccess     — "Créneau bloqué" / "Slot blocked"
pro.calendar.unblockSuccess   — "Créneau débloqué" / "Slot unblocked"
pro.calendar.legend.today     — "Aujourd'hui" / "Today"
pro.calendar.legend.blocked   — "Créneau bloqué" / "Blocked slot"
pro.calendar.legend.closed    — "Fermé" / "Closed"
```

## Testing

### Backend
- Create block: validates date not in past, endTime > startTime, fullDay nullifies times
- List: returns only future blocks, ordered by date
- Delete: removes block by id

### Frontend
- Calendar renders correct number of days for current month
- Click on day shows detail panel
- Block form: full day toggle hides time fields
- Create block calls POST, refreshes list
- Unblock calls DELETE, refreshes list
- Closed days (from opening hours) are grayed out

## Security

- `/api/pro/blocked-slots` protected by authGuard + roleGuard(PRO)
- `/api/salon/{slug}/blocked-slots` public
- Tenant isolation via schema-per-tenant
