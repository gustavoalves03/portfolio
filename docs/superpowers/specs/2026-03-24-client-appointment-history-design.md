# Story 6.1 — Client Appointment History

## Overview

A client can view all their appointments (across multiple salons) in a single page with "Upcoming" and "Past" tabs. Data is read from a denormalized `CLIENT_BOOKING_HISTORY` table in the public schema (APPUSER), populated as a transactional mirror when bookings are created or their status changes.

**Approach:** New `ClientBookingHistory` entity in the public schema. Mirror row written atomically alongside the tenant booking via `ALTER SESSION SET CURRENT_SCHEMA` within the same JDBC transaction. New `GET /api/auth/me/bookings` endpoint. Frontend replaces the admin CRUD `/bookings` page with a client-friendly appointment list.

## Backend — Entity

New entity in public schema (APPUSER):

```
CLIENT_BOOKING_HISTORY
├── id              (PK, IDENTITY)
├── user_id         (BIGINT, FK → USERS.id)
├── tenant_slug     (VARCHAR 100)
├── salon_name      (VARCHAR 255)
├── booking_id      (BIGINT — reference to CARE_BOOKINGS.id in tenant schema, no FK)
├── care_name       (VARCHAR 255)
├── care_price      (INTEGER — cents)
├── care_duration   (INTEGER — minutes)
├── appointment_date (DATE)
├── appointment_time (TIMESTAMP — LocalTime)
├── status          (VARCHAR 20 — CONFIRMED / CANCELLED)
├── created_at      (TIMESTAMP — Instant)
```

Package: `com.prettyface.app.bookings.domain.ClientBookingHistory`

This table is **not tenant-scoped** — it lives in APPUSER and Hibernate must always query it in the default schema context, never within a tenant schema.

## Backend — Repository

`ClientBookingHistoryRepository` in `com.prettyface.app.bookings.repo`:

```java
// Upcoming: CONFIRMED, date >= today, sorted ASC
List<ClientBookingHistory> findByUserIdAndStatusAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscAppointmentTimeAsc(
    Long userId, String status, LocalDate fromDate);

// Past: any status, date < today, sorted DESC
List<ClientBookingHistory> findByUserIdAndAppointmentDateBeforeOrderByAppointmentDateDescAppointmentTimeDesc(
    Long userId, LocalDate beforeDate);
```

## Backend — Transactional Mirror Write

In `CareBookingService.createClientBooking()`, after saving the `CareBooking` in the tenant schema:

1. Use JDBC to `ALTER SESSION SET CURRENT_SCHEMA = "APPUSER"` (switch to public schema)
2. Save `ClientBookingHistory` via its JPA repository
3. Use JDBC to `ALTER SESSION SET CURRENT_SCHEMA = "<TENANT>"` (switch back)
4. All within the same `@Transactional` method — Oracle treats this as a single transaction, so both writes are atomic

The `ClientBookingHistory` row is built from the already-resolved `User`, `Care`, `CareBooking`, and `salonName`/`tenantSlug` parameters.

**Status sync method** (for future Stories 6.2, 6.6):

```java
void updateMirrorStatus(String tenantSlug, Long bookingId, CareBookingStatus newStatus)
```

This updates the `CLIENT_BOOKING_HISTORY.status` column matching `(tenantSlug, bookingId)`. Called whenever a booking status changes in any tenant schema.

## Backend — Endpoint

New controller: `ClientBookingHistoryController` in `com.prettyface.app.bookings.web`

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `GET` | `/api/auth/me/bookings` | Authenticated | List current user's bookings |

**Query params:**
- `tab` (optional): `upcoming` (default) or `past`

**Logic:**
1. Extract userId from `@AuthenticationPrincipal`
2. No TenantContext needed — query runs on public schema
3. If `tab=upcoming`: query CONFIRMED bookings where date >= today, sorted ASC
4. If `tab=past`: query all bookings where date < today, sorted DESC

**Response DTO:** `ClientBookingHistoryResponse`

```java
record ClientBookingHistoryResponse(
    Long id,
    Long bookingId,
    String tenantSlug,
    String salonName,
    String careName,
    Integer carePrice,
    Integer careDuration,
    String appointmentDate,   // "yyyy-MM-dd"
    String appointmentTime,   // "HH:mm"
    String status,
    String createdAt
) {}
```

**Security:** The endpoint is under `/api/auth/**` which is already `permitAll()` in SecurityConfig, but it requires `@AuthenticationPrincipal` to resolve the user. Since it reads from the public schema (no tenant context), no tenant-related security is needed.

**Note:** This endpoint should be moved to a dedicated path like `/api/client/bookings` in a future refactor if more client-specific endpoints emerge. For now `/api/auth/me/bookings` keeps it simple and consistent with the existing `/api/auth/me` endpoint.

## Frontend — Page "Mes rendez-vous"

### Route change

- `/bookings` → client appointment history (protected by `authGuard`, no role restriction)
- Move existing admin CRUD bookings component to `/pro/bookings` (protected by `authGuard` + `roleGuard(PRO)`)

### New component: `ClientBookingsComponent`

Location: `frontend/src/app/pages/client-bookings/`

**Layout:**
- Page title "Mes rendez-vous"
- Two Material tabs: "À venir" / "Passés"
- Each tab shows a list of booking cards

**Booking card:**
- Salon name (bold)
- Care name
- Date formatted (e.g., "Jeudi 28 mars 2026")
- Time (e.g., "10:00")
- Duration (e.g., "1h30")
- Status badge (CONFIRMED = green, CANCELLED = grey)

**Empty states:**
- Upcoming empty: "Aucun rendez-vous à venir" + CTA "Découvrir les salons" → `/discover`
- Past empty: "Aucun rendez-vous passé"

### Service

New `ClientBookingHistoryService` or method in existing service:

```typescript
getMyBookings(tab: 'upcoming' | 'past'): Observable<ClientBookingHistoryResponse[]> {
  return this.http.get<ClientBookingHistoryResponse[]>(
    `${this.apiBaseUrl}/api/auth/me/bookings`, { params: { tab } }
  );
}
```

### Store

Simple signal-based state (no NgRx SignalStore needed for a read-only list):

```typescript
readonly upcomingBookings = signal<ClientBookingHistoryResponse[]>([]);
readonly pastBookings = signal<ClientBookingHistoryResponse[]>([]);
readonly activeTab = signal<'upcoming' | 'past'>('upcoming');
readonly loading = signal(false);
```

## i18n Keys

Add to both `fr.json` and `en.json`:

```
clientBookings.title          → "Mes rendez-vous" / "My Appointments"
clientBookings.upcoming       → "À venir" / "Upcoming"
clientBookings.past           → "Passés" / "Past"
clientBookings.emptyUpcoming  → "Aucun rendez-vous à venir" / "No upcoming appointments"
clientBookings.emptyPast      → "Aucun rendez-vous passé" / "No past appointments"
clientBookings.discoverCta    → "Découvrir les salons" / "Discover salons"
clientBookings.status.CONFIRMED → "Confirmé" / "Confirmed"
clientBookings.status.CANCELLED → "Annulé" / "Cancelled"
```

## Files Changed Summary

### Backend — New Files
- `bookings/domain/ClientBookingHistory.java` — JPA entity (public schema)
- `bookings/repo/ClientBookingHistoryRepository.java` — Spring Data repository
- `bookings/web/ClientBookingHistoryController.java` — GET endpoint
- `bookings/web/dto/ClientBookingHistoryResponse.java` — Response DTO

### Backend — Modified Files
- `bookings/app/CareBookingService.java` — add mirror write in `createClientBooking()`, add `updateMirrorStatus()` method
- `config/DataSourceConfig.java` — may need helper for schema switching within a transaction

### Frontend — New Files
- `pages/client-bookings/client-bookings.component.ts` — appointment history page
- `pages/client-bookings/client-bookings.component.html` — template with tabs and cards
- `pages/client-bookings/client-bookings.component.scss` — styles

### Frontend — Modified Files
- `app.routes.ts` — change `/bookings` to ClientBookingsComponent, add `/pro/bookings` for admin CRUD
- `public/i18n/fr.json` — add clientBookings keys
- `public/i18n/en.json` — add clientBookings keys
- Navigation routes — update links
