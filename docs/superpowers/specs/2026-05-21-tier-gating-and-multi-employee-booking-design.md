# Tier Feature Gating + Multi-Employee Booking — Design

**Date:** 2026-05-21
**Status:** Spec — awaiting implementation plan
**Scope:** Two independent server-side chantiers shipped sequentially. Chantier B (booking) ships first as it resolves a functional bug; chantier T (gating) ships next to unlock commercialisation by tier.

---

## 1. Problem statement

### 1.1 Tier feature gating (Chantier T)
The pricing page (`pricing-page.component.ts:89-136`) promises a differentiated matrix between VITRINE, GESTION and PREMIUM tiers (online booking, multi-staff, photos, SMS, shop, loyalty, multi-location…). The backend `SubscriptionGuard` (`SubscriptionGuard.java:21-74`) only checks `SubscriptionStatus.grantsAccess()`; it never reads `SubscriptionTier`. Frontend `AuthService.currentUser` does not expose the active tier either. Consequence: a VITRINE subscriber gets the same functionality as a PREMIUM subscriber. The product cannot be commercialised by tier.

### 1.2 Multi-employee booking (Chantier B)
The `CARE_BOOKINGS` table has an `EMPLOYEE_ID` column (`V1__baseline.sql:73-86`), the booking stepper already lets the client pick "first available" or a specific employee, but:
- The unique constraint is `UK_BOOKING_SLOT(appointment_date, appointment_time, care_id)` — slot is global to the salon, ignoring `employee_id`.
- `SlotAvailabilityService.getAvailableSlots()` (`SlotAvailabilityService.java:61-103`) does not filter or partition by `employee_id`.

Consequence: if Marie is booked at 10h, Sophie and Léa become unbookable at 10h even though they are free.

---

## 2. Goals & Non-goals

**Goals**
- Tier features are **per-tenant feature flags** (default seeded by tier, admin overrideable per tenant). Code never reads `SubscriptionTier` directly; it reads `FeatureFlagService.isEnabled(featureKey)`.
- Downgrades **never destroy data**; features become hidden but rows remain. Re-upgrade restores access.
- Disabled features render as **read-only with an upsell overlay**; mutations return 403 `FEATURE_DISABLED`.
- A `CARE_BOOKINGS` row is unique per `(date, time, employee_id)` for active statuses, so N employees ⇒ N parallel bookings per slot.
- "Premier disponible" resolves to a concrete employee **at booking creation time** using a **least-loaded-of-day** strategy (tie-breaker: name asc).
- Slot availability response carries the per-employee state so the UI can grey out individual employees (busy / on leave) while keeping the slot visible.
- Employee qualification is strict: only employees with `careId ∈ assignedCares` are considered.
- Employee absences are first-class and grey out the employee for the period covered.
- Concurrent booking attempts on the same `(date, time, employee_id)` are arbitrated by an Oracle unique index — second attempt receives 409 `SLOT_TAKEN`.

**Non-goals (explicitly out of scope)**
- Per-employee weekly schedule (Mon 9-17, Tue off…). Phase 1 assumes employee working hours = salon opening hours.
- Multi-care booking cart.
- Two-phase "RESERVING" status with TTL.
- Auditing/migration of every existing endpoint to the new gating system. Phase 1 adds gating to the features the pricing page explicitly promises; the rest is a follow-up audit.

---

## 3. Chantier T — Tier feature gating

### 3.1 Feature catalog
Enum `FeatureKey` (backend, shared with frontend translation keys):

| Key | Default tiers |
|---|---|
| `BOOKING` | GESTION, PREMIUM |
| `EMPLOYEES` | GESTION, PREMIUM |
| `PHOTOS` | GESTION, PREMIUM |
| `SMS_REMINDER` | GESTION, PREMIUM |
| `CLIENT_FILES` | GESTION, PREMIUM |
| `ABSENCE_MGMT` | GESTION, PREMIUM |
| `ONLINE_PAYMENT` | PREMIUM |
| `SHOP` | PREMIUM |
| `LOYALTY` | PREMIUM |
| `MULTI_LOCATION` | PREMIUM |

VITRINE gets no feature flag (public landing page only — not gated per feature, gated by absence of `BOOKING`/`EMPLOYEES`).

`TierFeatureCatalog` is an immutable `Map<SubscriptionTier, Set<FeatureKey>>` defined in code.

### 3.2 Persistence (per-tenant Flyway migration)
`V7__tenant_features.sql` (next tenant migration after current V6):
```sql
CREATE TABLE TENANT_FEATURES (
  FEATURE_KEY VARCHAR2(64) PRIMARY KEY,
  ENABLED     NUMBER(1) NOT NULL CHECK (ENABLED IN (0,1)),
  SOURCE      VARCHAR2(16) NOT NULL CHECK (SOURCE IN ('TIER_DEFAULT','ADMIN_OVERRIDE')),
  UPDATED_AT  TIMESTAMP WITH TIME ZONE NOT NULL
);
```
- `FEATURE_KEY` is unique per tenant schema (multi-tenant Flyway already namespaces).
- `SOURCE = ADMIN_OVERRIDE` rows are preserved across tier changes; `TIER_DEFAULT` rows are recomputed on every tier change.
- New rows on tenant provisioning seeded from `TierFeatureCatalog[currentTier]`.

Legacy tenants: mirror the `CREATE TABLE` in the Java `migrateOracleSchema()` boot path (per `feedback_legacy_tenant_create_table.md`).

### 3.3 Service layer
`FeatureFlagService`:
- `boolean isEnabled(FeatureKey)` — Caffeine cache, TTL 60s, evicted on override.
- `Map<FeatureKey,Boolean> snapshot()` — for `/api/me/features`.
- `void applyTierDefaults(SubscriptionTier newTier)` — upsert all keys: rows with `SOURCE=ADMIN_OVERRIDE` are untouched; otherwise aligned to `TierFeatureCatalog[newTier]`.
- `void overrideForTenant(FeatureKey, boolean)` — admin only, marks `SOURCE=ADMIN_OVERRIDE`, evicts cache for the key.

Hook: `SubscriptionService.changeTier(...)` calls `featureFlagService.applyTierDefaults(newTier)` in the same transaction.

### 3.4 Enforcement
- `@RequiresFeature(FeatureKey.SHOP)` annotation + `FeatureGateAspect` (Spring AOP `@Aspect`).
- On disabled feature, throws `FeatureDisabledException(featureKey)` → mapped by `GlobalExceptionHandler` to:
  ```json
  HTTP 403
  { "error": "FEATURE_DISABLED", "featureKey": "SHOP", "minimumTier": "PREMIUM" }
  ```
- Annotations added to: every booking, employees, photos, SMS, client files, absence, online payment, shop, loyalty, multi-location controller method (mutation + read).

### 3.5 API
- `GET /api/me/features` → `{ booking: true, shop: false, ... }` consumed by Angular store at login.
- `PUT /api/admin/tenants/{tenantId}/features/{key}` body `{ enabled: boolean }` — admin only, audit log written.

### 3.6 Frontend
- `FeatureFlagsStore` (NgRx SignalStore): `withState({ flags: {} })`, loaded after auth via `/api/me/features`, exposes `isEnabled(key: FeatureKey)` as computed.
- Directive `*lpFeatureEnabled="'SHOP'"` — completely removes the host element when disabled (use for nav items, route guards via `canActivate`).
- Component `<lp-feature-locked feature="SHOP">…</lp-feature-locked>` — wraps a section; when disabled, renders an overlay "Disponible avec Premium" + CTA → `/pricing`. Inner content stays in DOM in read-only mode (no inputs, no buttons).
- HTTP interceptor: on `403 FEATURE_DISABLED`, fires a toast `errors.features.disabled` + navigates to `/pricing` highlighting the missing feature.
- Existing stores untouched: gating is purely presentational/HTTP — no domain code refactor.

### 3.7 Test cases
1. VITRINE → menu "Réservations" hidden, `POST /api/bookings` → 403 FEATURE_DISABLED.
2. GESTION → `/api/products` → 403 FEATURE_DISABLED with `minimumTier: PREMIUM`.
3. GESTION + admin override `SHOP=true` → access granted to `/api/products`.
4. PREMIUM, admin sets `LOYALTY=false` → UI hides loyalty, mutation 403.
5. PREMIUM default → all flags `true`.
6. VITRINE on `/pro/employees` page → renders `<lp-feature-locked>` overlay, content read-only.
7. VITRINE → `POST /api/pro/employees` → 403 FEATURE_DISABLED.
8. PREMIUM tenant with 3 employees downgrades to VITRINE → rows in `EMPLOYEE` table untouched, UI hides them.
9. Same tenant re-upgrades to GESTION → employees reappear.
10. Bookings whose `employee_id` references a still-existing row remain consistent across downgrade.
11. New pro signs up on GESTION → `TENANT_FEATURES` seeded with GESTION defaults.
12. Tier change VITRINE → PREMIUM: `TIER_DEFAULT` rows recomputed, `ADMIN_OVERRIDE` rows preserved.

---

## 4. Chantier B — Multi-employee booking

### 4.1 Schema migration (per-tenant Flyway)
`V8__booking_per_employee.sql` (runs after V7 above):

```sql
-- 1. Add CANCELLATION_REASON column (doesn't exist yet on CARE_BOOKINGS)
ALTER TABLE CARE_BOOKINGS ADD CANCELLATION_REASON VARCHAR2(64);

-- 2. Backfill: assign legacy NULL employee_id on active bookings.
--    Strategy: pick MIN(ID) of an active employee from EMPLOYEES table.
--    "Default self-employee" backfill already ran via tenant V4, so every
--    tenant should have at least one active employee at this point.
UPDATE CARE_BOOKINGS
   SET EMPLOYEE_ID = (
     SELECT MIN(e.ID) FROM EMPLOYEES e WHERE e.ACTIVE = 1
   )
 WHERE EMPLOYEE_ID IS NULL
   AND STATUS IN ('PENDING','CONFIRMED');

-- Safety net: any bookings still NULL (no active employee at all) -> cancelled
UPDATE CARE_BOOKINGS
   SET STATUS = 'CANCELLED', CANCELLATION_REASON = 'LEGACY_NO_EMPLOYEE'
 WHERE EMPLOYEE_ID IS NULL
   AND STATUS IN ('PENDING','CONFIRMED');

-- 3. Drop the old global slot uniqueness
ALTER TABLE CARE_BOOKINGS DROP CONSTRAINT UK_BOOKING_SLOT;

-- 4. Per-employee uniqueness on active statuses (Oracle: function-based unique index).
--    NULL values are excluded from unique index in Oracle, so CANCELLED rows do not conflict.
CREATE UNIQUE INDEX UK_BOOKING_SLOT_EMPLOYEE ON CARE_BOOKINGS (
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_DATE END,
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_TIME END,
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN EMPLOYEE_ID END
);

-- 5. Lookup performance
CREATE INDEX IDX_BOOKING_DATE_EMPLOYEE ON CARE_BOOKINGS (APPOINTMENT_DATE, EMPLOYEE_ID);
```

**No new `EMPLOYEE_ABSENCE` table is created.** The existing `LEAVE_REQUESTS` table (created in tenant V1) already models employee absences with workflow `PENDING|APPROVED|REJECTED|CANCELLED`. Section 4.3 below uses `LeaveRequest` rows with `status = APPROVED` as the source of truth for "employee on leave at this slot".

Mirror the `ALTER TABLE` / `DROP CONSTRAINT` / `CREATE INDEX` statements in `TenantSchemaMigrator` Java boot path for legacy tenants (idempotent via try/catch on already-applied) — see `feedback_legacy_tenant_create_table.md`.

### 4.2 Domain — reuse existing `LeaveRequest` entity

Use existing `LeaveRequest` (with `@Table("LEAVE_REQUESTS")`) — no new entity needed. A leave with `status = APPROVED` and `startDate <= queryDate <= endDate` means the employee is unavailable that day. Phase 1 treats absences as full-day (column types `LocalDate`); finer granularity is out of scope.

Add to `LeaveRequestRepository`:
```java
List<LeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
    LeaveStatus status, LocalDate from, LocalDate to);

@Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' " +
       "AND lr.employee.id IN :employeeIds " +
       "AND lr.startDate <= :date AND lr.endDate >= :date")
List<LeaveRequest> findApprovedLeavesCovering(@Param("employeeIds") Collection<Long> employeeIds,
                                              @Param("date") LocalDate date);
```

### 4.3 Service layer

`SlotAvailabilityService.getAvailableSlots(LocalDate date, Long careId)`:
```
1. employees = employeeRepo.findActiveByTenant()
              .filter(e -> e.assignedCares.contains(careId))
   if employees.isEmpty() → return [] + emit log "NO_QUALIFIED_EMPLOYEE"
2. salonHours = openingHoursService.forDate(date)
   slots = generateSlots(salonHours, careDuration)
3. activeBookings = bookingRepo.findByDateAndStatusIn(date, [PENDING,CONFIRMED])
   absences = absenceRepo.findByEmployeesAndDate(employees, date)
4. For each slot:
     perEmployee = employees.map(e -> {
       if absences.covers(e.id, slot) → { id:e.id, name:e.name, available:false, reason:'ON_LEAVE' }
       else if activeBookings.has(e.id, slot) → { id, name, available:false, reason:'BUSY' }
       else → { id, name, available:true }
     })
     keep slot iff perEmployee.anyMatch(available=true)
     output: { time: slot, employees: perEmployee }
```

`EmployeeAssignmentService.pickLeastLoaded(LocalDate date, LocalTime time, Long careId)`:
```
1. candidates = employees(active ∩ assignedCares.contains(careId))
                  ∖ absent at (date,time)
                  ∖ already booked at (date,time)
2. if candidates.isEmpty() → throw NoEmployeeAvailableException
3. countsByEmployee = bookingRepo.countActiveByEmployeeAndDate(date)
4. return candidates.stream()
     .sorted(Comparator.comparingInt(e -> countsByEmployee.getOrDefault(e.id,0))
                       .thenComparing(e -> e.name))
     .findFirst().get();
```

`BookingService.create(request)`:
- If `request.employeeId != null`:
  1. Verify employee active + `assignedCares.contains(careId)` → else 400 `EMPLOYEE_NOT_QUALIFIED`.
  2. Verify not on leave → else 409 `EMPLOYEE_ON_LEAVE`.
  3. Insert. On Oracle constraint violation `UK_BOOKING_SLOT_EMPLOYEE` → 409 `SLOT_TAKEN`.
- Else (`employeeId == null`, "Premier dispo"):
  1. Call `pickLeastLoaded(date, time, careId)`.
  2. Insert. On UK violation → retry pickLeastLoaded once (the chosen employee was just taken). After 2 failed attempts → 409 `NO_EMPLOYEE_AVAILABLE`.

`BookingService.cancel(id)`:
- Update `STATUS = 'CANCELLED'`. The functional unique index excludes cancelled rows ⇒ slot/employee immediately re-bookable.

### 4.4 API changes

**`GET /api/salon/{slug}/availability?careId={id}&date={iso}`** — payload becomes:
```json
{
  "date": "2026-06-01",
  "slots": [
    {
      "time": "09:00",
      "employees": [
        { "id": 1, "name": "Marie", "available": true },
        { "id": 2, "name": "Sophie", "available": false, "reason": "BUSY" },
        { "id": 3, "name": "Léa", "available": false, "reason": "ON_LEAVE" }
      ]
    }
  ]
}
```

**`POST /api/bookings`** — body unchanged (`employeeId` optional). New error responses: `SLOT_TAKEN`, `EMPLOYEE_NOT_QUALIFIED`, `EMPLOYEE_ON_LEAVE`, `NO_EMPLOYEE_AVAILABLE`.

**Existing `EmployeeLeaveController` endpoints** (`POST/GET/PATCH` on `/api/pro/employees/{id}/leaves`) gain `@RequiresFeature(ABSENCE_MGMT)`. No new endpoint introduced — section 21.1 of pricing matrix is satisfied by gating the existing leave workflow.

### 4.5 Frontend changes
- `SlotWithEmployees` DTO + new response type in `BookingsService`.
- `step-time.component`: each slot row renders the time + a horizontal list of employee chips. Available employees clickable; busy/on-leave greyed with tooltip (`errors.booking.employeeBusy`, `errors.booking.employeeOnLeave`).
- `step-care.component`: when client picks "Premier dispo", `employeeId` stays `null`; when client picks a specific employee, slot list filters to slots where that employee has `available=true`.
- Existing employee leaves UI gains `*lpFeatureEnabled="'ABSENCE_MGMT'"`. No new component.
- Error handling: `409 SLOT_TAKEN` → toast `errors.booking.slotTaken` + refresh slots; `409 NO_EMPLOYEE_AVAILABLE` → toast + back to step-time.
- All new user-facing strings added to both `fr.json` and `en.json`.

### 4.6 Test cases
13. 3 qualified employees, client A books Marie at 10h → 10h slot still returned, Marie greyed.
14. Clients B and C book Sophie and Léa at 10h → 10h slot no longer returned.
15. Marie 0 / Sophie 2 / Léa 3 RDV today → "Premier dispo" assigns Marie.
16. Tie Marie 0 / Sophie 0 → alphabetical tie-breaker assigns Marie.
17. All 3 busy at 10h → "Premier dispo" returns 409 NO_EMPLOYEE_AVAILABLE.
18. Massage assigned only to Sophie; Sophie busy at 14h → slot 14h absent from response.
19. No employee has `assignedCares.contains(careId)` → empty slots + explicit message.
20. Sophie on leave 2026-06-01 → grey all day.
21. Sophie on leave 09:00-12:00 → grey morning, available afternoon.
22. All employees on leave that day → no slot returned for that day.
23. Clients A and B race-book Sophie at 10h → one wins (200), the other 409 SLOT_TAKEN.
24. Client A picks "Premier dispo" after Marie was just taken → re-pick selects next least-loaded (Sophie).
25. Marie 10h cancelled → slot immediately re-bookable.
26. After cancel, "Premier dispo" again may choose Marie (back in candidate pool).
27. Legacy bookings with `EMPLOYEE_ID = NULL` are backfilled by V18 or cancelled with reason `LEGACY_NO_EMPLOYEE`.

---

## 5. Testing strategy

- **Unit tests (JUnit + Mockito)**:
  - `FeatureFlagServiceTests` — cache, override admin, `applyTierDefaults` preserves overrides (TC 1-5, 8-12).
  - `EmployeeAssignmentServiceTests` — least-loaded, alpha tie-breaker, no candidate (TC 15-17, 23-24).
  - `SlotAvailabilityServiceTests` — strict qualification, absence overlap, empty slot pruning (TC 13-14, 18-22).
- **Integration tests Spring**:
  - `@DataJpaTest` for repositories on H2 (basic schema sanity).
  - **Testcontainers Oracle** smoke test for: concurrent insert hitting `UK_BOOKING_SLOT_EMPLOYEE` (TC 23), function-based unique index correctness, `TIMESTAMP WITH TIME ZONE` round-trip for `Absence` (per `project_pending_testcontainers_oracle.md`).
- **Controller slice tests `@WebMvcTest`**: `FeatureGateAspect` returns 403 with correct payload (TC 1, 2, 7).
- **Frontend Karma/Jasmine**: stepper UX (TC 13-14), gated component overlay rendering (TC 6).
- **Manual E2E on staging**: full upgrade/downgrade cycle (TC 8-12) and one booking race (TC 23) before prod migration.

## 6. Error catalog

| Code | HTTP | Context payload |
|---|---|---|
| `FEATURE_DISABLED` | 403 | `featureKey`, `minimumTier` |
| `SLOT_TAKEN` | 409 | `date`, `time`, `employeeId` |
| `NO_EMPLOYEE_AVAILABLE` | 409 | `date`, `time`, `careId` |
| `EMPLOYEE_NOT_QUALIFIED` | 400 | `employeeId`, `careId` |
| `EMPLOYEE_ON_LEAVE` | 409 | `employeeId`, `from`, `to` |

Each gets a translation key under `errors.features.*` / `errors.booking.*` in `fr.json` + `en.json`.

## 7. Risks & mitigations

1. **Legacy bookings with `EMPLOYEE_ID = NULL`** — Before V18 in prod, count affected rows. If > 0, run the backfill UPDATE; if no active employee exists for a tenant, fall back to cancellation with explicit reason. Document the prod migration runbook in the implementation plan.
2. **Oracle does not support `WHERE` on unique indexes** — Use function-based unique index (`CASE WHEN STATUS IN (...) THEN col END`). Validate the syntax via Testcontainers Oracle before merging.
3. **Cache staleness on admin override** — `FeatureFlagService.overrideForTenant` must evict the key after persist. TTL 60s acceptable as fallback.
4. **Legacy tenant boot path** — `migrateOracleSchema()` Java must idempotently create `TENANT_FEATURES`, `EMPLOYEE_ABSENCE`, and execute the V18 schema changes (per `feedback_legacy_tenant_create_table.md`).
5. **Sequential execution gate** — Chantier B must ship before commercialisation messaging promises multi-praticien correctness; Chantier T can ship the same week but should not be enforced on the production tenants until B is verified.
6. **No silent fallback when zero qualified employees** — Return empty slot list with a user-facing message instead of pretending the salon is closed.

## 8. Out of scope (next cycles)

- Per-employee weekly working hours (Mon 9-17, Tue off…). Phase 1 treats employee hours as salon hours.
- Multi-care booking cart.
- Two-phase `RESERVING` status with TTL.
- Audit of every existing endpoint for tier-gating completeness. Phase 1 gates the endpoints the pricing page promises; the rest is a follow-up audit ticket.
- Loyalty / shop / online payment / multi-location *implementation* — only their *gating* is in scope here; the features themselves either exist already or will be specced separately.
