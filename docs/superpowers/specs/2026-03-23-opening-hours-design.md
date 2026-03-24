# Story 4.1 — Weekly Opening Hours Configuration

## Overview

Allow beauty professionals to define their weekly opening hours with multiple time slots per day (e.g., morning + afternoon). Data stored per tenant schema. Inline editing with bulk save.

**Approach:** New `OpeningHour` entity in tenant schema. Bulk PUT replaces all hours at once. Frontend page at `/pro/availability` with vertical list layout, inline time pickers, and a single save button.

## Backend — Entity

New entity in tenant schema:

```
OPENING_HOURS
├── id (PK, IDENTITY)
├── day_of_week (INTEGER, 1=Monday...7=Sunday)
├── open_time (TIME)
├── close_time (TIME)
```

- No `isClosed` field — closed day = no rows for that day
- Multiple rows per day allowed (e.g., 09:00-12:00 + 14:00-18:00)
- Validation: `closeTime > openTime`, no overlapping slots for the same day

## Backend — API

New feature package: `com.prettyface.app.availability`

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `GET` | `/api/pro/opening-hours` | PRO | List all slots for current tenant |
| `PUT` | `/api/pro/opening-hours` | PRO | Replace all slots (bulk save) |
| `GET` | `/api/salon/{slug}/opening-hours` | Public | Get public opening hours |

### DTOs

```java
record OpeningHourRequest(
    @NotNull Integer dayOfWeek,   // 1-7
    @NotNull String openTime,     // "09:00"
    @NotNull String closeTime     // "18:00"
) {}

record OpeningHourResponse(
    Long id,
    Integer dayOfWeek,
    String openTime,
    String closeTime
) {}
```

### PUT Logic

1. Validate all entries (dayOfWeek 1-7, closeTime > openTime, no overlaps per day)
2. Delete all existing `OpeningHour` rows for this tenant
3. Insert all new rows from request
4. Return the saved list

### Public Endpoint

Add to existing `PublicSalonController`:
```java
@GetMapping("/{slug}/opening-hours")
```
Sets TenantContext, queries OpeningHourRepository, returns list.

### Overlap Validation

For each day, sort slots by openTime. Check that each slot's openTime >= previous slot's closeTime. If overlap detected → 400 Bad Request.

## Frontend — Page `/pro/availability`

### Component

New lazy-loaded component at `/pro/availability`:
- `AvailabilityComponent` with inline template or separate files
- Manages a local signal `weekSlots: Signal<DaySlots[]>` (7 days, each with array of time slots)
- On init: loads from `GET /api/pro/opening-hours`
- On save: sends `PUT /api/pro/opening-hours` with flattened list

### Data Model

```typescript
interface TimeSlot {
  openTime: string;  // "09:00"
  closeTime: string; // "18:00"
}

interface DaySlots {
  dayOfWeek: number; // 1-7
  slots: TimeSlot[];
}

interface OpeningHourResponse {
  id: number;
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}
```

### Layout (Vertical List)

- 7 rows (Monday → Sunday)
- Each row: day name | time slots inline (editable `<input type="time">`) | "+" button
- Closed days: grayed out, "Fermé" text, click to open (adds default 09:00-18:00 slot)
- Open days: "×" on each slot to remove, removing last slot closes the day
- Single "Enregistrer" button at bottom

### Service

`AvailabilityService`:
```
loadHours(): Observable<OpeningHourResponse[]>      → GET /api/pro/opening-hours
saveHours(hours: OpeningHourRequest[]): Observable<OpeningHourResponse[]> → PUT /api/pro/opening-hours
loadPublicHours(slug: string): Observable<OpeningHourResponse[]> → GET /api/salon/{slug}/opening-hours
```

### Store

`AvailabilityStore` with NgRx SignalStore:
- State: `hours: OpeningHourResponse[]`
- Methods: `loadHours()`, `saveHours()`
- Request status tracking (pending/fulfilled/error)

## Routing

Add to `pro` children in `app.routes.ts`:
```typescript
{
  path: 'availability',
  loadComponent: () => import('./features/availability/availability.component').then(m => m.AvailabilityComponent),
}
```

## Navigation

Add to `PRO_NAVIGATION_ROUTES` in `navigation-routes.ts`:
```typescript
{
  label: 'Disponibilités',
  path: '/pro/availability',
  icon: 'schedule',
  requiresAuth: true,
  requiredRole: 'PRO'
}
```

## Internationalization

Keys in both `fr.json` and `en.json`:

```
pro.availability.title           — "Mes disponibilités" / "My availability"
pro.availability.closed          — "Fermé" / "Closed"
pro.availability.addSlot         — "Ajouter une plage" / "Add time slot"
pro.availability.save            — "Enregistrer" / "Save"
pro.availability.saveSuccess     — "Horaires mis à jour" / "Hours updated"
pro.availability.saveError       — "Erreur lors de la sauvegarde" / "Error saving hours"
pro.availability.clickToOpen     — "Cliquez pour ouvrir" / "Click to open"
pro.availability.overlapError    — "Les plages horaires se chevauchent" / "Time slots overlap"
pro.availability.invalidTime     — "L'heure de fin doit être après l'heure de début" / "End time must be after start time"
pro.availability.days.1          — "Lundi" / "Monday"
pro.availability.days.2          — "Mardi" / "Tuesday"
pro.availability.days.3          — "Mercredi" / "Wednesday"
pro.availability.days.4          — "Jeudi" / "Thursday"
pro.availability.days.5          — "Vendredi" / "Friday"
pro.availability.days.6          — "Samedi" / "Saturday"
pro.availability.days.7          — "Dimanche" / "Sunday"
```

## Testing

### Backend
- Service: validation (overlap detection, invalid times, dayOfWeek range)
- Service: bulk save replaces existing data
- Controller: GET returns grouped by day, PUT validates and saves

### Frontend
- 7 day rows render
- Closed day shows "Fermé", click opens with default slot
- Add slot adds new time range
- Remove slot, removing last closes the day
- Save button calls PUT with correct payload
- Snackbar feedback on success/error

## Security

- `/api/pro/opening-hours` protected by authGuard + roleGuard(PRO)
- `/api/salon/{slug}/opening-hours` public (no auth)
- Tenant isolation via schema-per-tenant (queries only see current tenant's data)
