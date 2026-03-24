# Story 6.1 — Client Appointment History

## Overview

A client can view all their appointments (across multiple salons) in a single page with "Upcoming" and "Past" tabs. Data is read from a denormalized `CLIENT_BOOKING_HISTORY` table in the public schema (APPUSER), populated as a mirror when bookings are created or their status changes.

**Approach:** New `ClientBookingHistory` entity in the public schema with `@Table(schema = "APPUSER")`. Mirror row written from the controller layer (after TenantContext is cleared) to avoid Hibernate multi-tenant connection corruption. New `GET /api/client/me/bookings` endpoint under a dedicated authenticated path. Frontend replaces the admin CRUD `/bookings` page with a client-friendly appointment list.

## Backend — Entity

New entity in public schema (APPUSER), annotated with `@Table(name = "CLIENT_BOOKING_HISTORY", schema = "APPUSER")` to ensure Hibernate always resolves to the public schema regardless of TenantContext state.

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
├── appointment_date (DATE — LocalDate)
├── appointment_time (TIMESTAMP — LocalTime)
├── status          (VARCHAR 20 — CONFIRMED / CANCELLED)
├── created_at      (TIMESTAMP — Instant)
```

Package: `com.prettyface.app.bookings.domain.ClientBookingHistory`

**DDL strategy:** Hibernate `ddl-auto=update` creates this table in the APPUSER schema at startup (the `schema = "APPUSER"` annotation directs Hibernate). No manual DDL or TenantSchemaManager change needed.

**TenantContext behavior for CLIENT users:** The `TenantFilter` only sets context for PRO users (via `findByOwnerId`). For CLIENT users, `TenantContext` remains null, and `TenantIdentifierResolver` resolves to `APPUSER` (the default). This means queries on `ClientBookingHistory` naturally hit the public schema.

## Backend — Repository

`ClientBookingHistoryRepository` in `com.prettyface.app.bookings.repo`:

```java
// Upcoming: CONFIRMED, date >= today, sorted ASC
List<ClientBookingHistory> findByUserIdAndStatusAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscAppointmentTimeAsc(
    Long userId, String status, LocalDate fromDate);

// Past: any status, date < today, sorted DESC
List<ClientBookingHistory> findByUserIdAndAppointmentDateBeforeOrderByAppointmentDateDescAppointmentTimeDesc(
    Long userId, LocalDate beforeDate);

// For status sync (Stories 6.2, 6.6)
Optional<ClientBookingHistory> findByTenantSlugAndBookingId(String tenantSlug, Long bookingId);
```

## Backend — Mirror Write Strategy

**Critical constraint:** Do NOT use `ALTER SESSION SET CURRENT_SCHEMA` inside a Hibernate-managed `@Transactional` method. Hibernate's `MultiTenantConnectionProvider` manages the connection's schema state, and manually switching schemas mid-transaction corrupts Hibernate's internal bookkeeping.

**Approach:** Write the mirror row from `PublicSalonController.book()`, AFTER the tenant booking completes and `TenantContext.clear()` is called in the finally block. At that point, the schema is back to APPUSER.

```
PublicSalonController.book():
1. Resolve tenant, client, owner from public schema
2. TenantContext.setCurrentTenant(slug)
3. try {
4.     result = careBookingService.createClientBooking(...)  // writes CARE_BOOKINGS in tenant
5. } finally {
6.     TenantContext.clear()  // back to APPUSER
7. }
8. clientBookingHistoryService.createMirror(client, result, tenantSlug, salonName)  // writes CLIENT_BOOKING_HISTORY in APPUSER
9. return 201 Created
```

**Trade-off:** This is NOT strictly atomic — the tenant booking commits first, then the mirror write happens. If the mirror write fails (extremely unlikely), the booking exists in the tenant but not in the client history. A future reconciliation job can catch discrepancies. This is acceptable because:
- The tenant booking is the source of truth
- The mirror is for client convenience
- Mirror write failure = missing row in history (not data corruption)

**Status sync method** (for future Stories 6.2, 6.6):

```java
void updateMirrorStatus(String tenantSlug, Long bookingId, String newStatus)
```

Updates `CLIENT_BOOKING_HISTORY.status` matching `(tenantSlug, bookingId)`. Called from the controller after the tenant status change completes.

## Backend — Endpoint

New controller: `ClientBookingHistoryController` in `com.prettyface.app.bookings.web`

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `GET` | `/api/client/me/bookings` | Authenticated | List current user's bookings |

**Security config:** Add new rule before the catch-all:
```java
.requestMatchers("/api/client/**").authenticated()
```

This puts the endpoint under a dedicated authenticated path, not under `/api/auth/**` which is `permitAll()` and CSRF-exempt.

**Query params:**
- `tab` (optional): `upcoming` (default) or `past`

**Logic:**
1. Extract userId from `@AuthenticationPrincipal`
2. No TenantContext needed — query runs on public schema (APPUSER)
3. If `tab=upcoming`: query CONFIRMED bookings where date >= today, sorted date ASC then time ASC
4. If `tab=past`: query all bookings where date < today, sorted date DESC then time DESC

**Date boundary:** Today's bookings always appear in "upcoming" regardless of their time. This is intentional — date-level granularity keeps the queries simple and matches user expectations (you see today's appointments until the day is over).

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

## Frontend — Page "Mes rendez-vous"

### Route change

- `/bookings` → `ClientBookingsComponent` (protected by `authGuard`, no role restriction)
- Move existing admin CRUD bookings component to `/pro/bookings` (protected by `authGuard` + `roleGuard(PRO)`)

### Bookings drawer

The header bookings drawer currently uses `BookingsService.listDetailed()` which is a tenant-scoped PRO endpoint. For CLIENT users it would fail. Hide the drawer for CLIENT users (only show for PRO/ADMIN).

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

New `ClientBookingHistoryService` in `frontend/src/app/features/client-bookings/`:

```typescript
getMyBookings(tab: 'upcoming' | 'past'): Observable<ClientBookingHistoryResponse[]> {
  return this.http.get<ClientBookingHistoryResponse[]>(
    `${this.apiBaseUrl}/api/client/me/bookings`, { params: { tab } }
  );
}
```

### Store

NgRx SignalStore for consistency with project patterns:

```typescript
export const ClientBookingsStore = signalStore(
  withState<{ upcoming: ClientBookingHistoryResponse[]; past: ClientBookingHistoryResponse[] }>({
    upcoming: [], past: []
  }),
  withRequestStatus(),
  withMethods((store, service = inject(ClientBookingHistoryService)) => ({
    loadUpcoming: rxMethod<void>(...),
    loadPast: rxMethod<void>(...),
  })),
  withHooks({ onInit(store) { store.loadUpcoming(); } })
);
```

## i18n Keys

Add to both `fr.json` and `en.json`:

```
clientBookings.title              → "Mes rendez-vous" / "My Appointments"
clientBookings.upcoming           → "À venir" / "Upcoming"
clientBookings.past               → "Passés" / "Past"
clientBookings.emptyUpcoming      → "Aucun rendez-vous à venir" / "No upcoming appointments"
clientBookings.emptyPast          → "Aucun rendez-vous passé" / "No past appointments"
clientBookings.discoverCta        → "Découvrir les salons" / "Discover salons"
clientBookings.status.CONFIRMED   → "Confirmé" / "Confirmed"
clientBookings.status.CANCELLED   → "Annulé" / "Cancelled"
```

## Files Changed Summary

### Backend — New Files
- `bookings/domain/ClientBookingHistory.java` — JPA entity with `@Table(schema = "APPUSER")`
- `bookings/repo/ClientBookingHistoryRepository.java` — Spring Data repository
- `bookings/app/ClientBookingHistoryService.java` — mirror write + status sync logic
- `bookings/web/ClientBookingHistoryController.java` — GET endpoint
- `bookings/web/dto/ClientBookingHistoryResponse.java` — Response DTO

### Backend — Modified Files
- `tenant/web/PublicSalonController.java` — add mirror write call after tenant booking
- `config/SecurityConfig.java` — add `/api/client/**` authenticated rule

### Frontend — New Files
- `pages/client-bookings/client-bookings.component.ts` — appointment history page
- `pages/client-bookings/client-bookings.component.html` — template with tabs and cards
- `pages/client-bookings/client-bookings.component.scss` — styles
- `features/client-bookings/client-bookings.service.ts` — HTTP service
- `features/client-bookings/client-bookings.store.ts` — NgRx SignalStore
- `features/client-bookings/client-bookings.model.ts` — TypeScript interfaces

### Frontend — Modified Files
- `app.routes.ts` — change `/bookings` to ClientBookingsComponent, add `/pro/bookings` for admin CRUD
- `shared/layout/header/bookings-drawer/` — hide drawer for CLIENT users
- `shared/layout/navigation/navigation-routes.ts` — update bookings link
- `public/i18n/fr.json` — add clientBookings keys
- `public/i18n/en.json` — add clientBookings keys
