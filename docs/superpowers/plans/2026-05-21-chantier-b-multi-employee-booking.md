# Chantier B — Multi-Employee Booking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `CARE_BOOKINGS` uniqueness per-employee so N employees can be booked at the same slot, and refactor availability/booking creation so the client UI shows a per-employee state (busy / on-leave / available) and auto-assigns the least-loaded employee for "Premier dispo".

**Architecture:**
1. Tenant Flyway migration `V7__booking_per_employee.sql` adds `CANCELLATION_REASON`, backfills NULL `EMPLOYEE_ID`, drops `UK_BOOKING_SLOT`, and creates function-based `UK_BOOKING_SLOT_EMPLOYEE` + `IDX_BOOKING_DATE_EMPLOYEE`.
2. `SlotAvailabilityService` gains a new public method returning `List<SlotWithEmployees>` that fans out per qualified employee, marking each as `available | BUSY | ON_LEAVE`.
3. New `EmployeeAssignmentService.pickLeastLoaded(...)` resolves "Premier dispo" using least-RDV-of-day + alpha tie-breaker.
4. `CareBookingService.create()` and `createClientBooking()` use the new path: validate qualification, validate not-on-leave, insert with retry on `DataIntegrityViolationException`. Error catalog gets 4 new codes (`SLOT_TAKEN`, `EMPLOYEE_NOT_QUALIFIED`, `EMPLOYEE_ON_LEAVE`, `NO_EMPLOYEE_AVAILABLE`).
5. Public availability endpoint (`/api/salon/{slug}/availability`) returns the new fanned-out payload; existing `step-time` / `step-care` frontend components consume per-employee chips with greyed-out states.

**Tech Stack:** Spring Boot 3.5 / Java 21, Oracle 23ai (Flyway tenant migrations), JPA, Mockito, Spring `@DataJpaTest` (H2), Testcontainers Oracle smoke tests, Angular 20 standalone + NgRx SignalStore, Karma/Jasmine, Transloco i18n.

**Spec:** `docs/superpowers/specs/2026-05-21-tier-gating-and-multi-employee-booking-design.md` §4 + §4.6 test cases.

---

## File Structure

| Path | Action | Responsibility |
|---|---|---|
| `backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql` | Create | Schema migration: drop UK, add function-based UK per-employee, backfill, CANCELLATION_REASON column |
| `backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java` | Modify | Mirror V7 statements for legacy tenants (idempotent) |
| `backend/src/main/java/com/luxpretty/app/bookings/domain/CareBooking.java` | Modify | Add `cancellationReason` field |
| `backend/src/main/java/com/luxpretty/app/bookings/web/dto/SlotWithEmployees.java` | Create | New DTO: `{ time, employees: [{id, name, available, reason}] }` |
| `backend/src/main/java/com/luxpretty/app/bookings/web/dto/EmployeeSlotState.java` | Create | New DTO: per-employee slot state |
| `backend/src/main/java/com/luxpretty/app/availability/app/SlotAvailabilityService.java` | Modify | Add `getAvailableSlotsForCareWithEmployees(date, careId)` returning fanned-out payload |
| `backend/src/main/java/com/luxpretty/app/bookings/app/EmployeeAssignmentService.java` | Create | `pickLeastLoaded(date, time, careId)` + retry-aware variant |
| `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java` | Modify | Validate qualification + leave; auto-assign on null employeeId; map DB collision → 409 SLOT_TAKEN |
| `backend/src/main/java/com/luxpretty/app/common/error/BookingErrorCodes.java` | Create | Error code constants used in 409/400 responses |
| `backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java` | Modify | New queries: `countActiveByEmployeeAndDate`, `findActiveByDateAndEmployees` |
| `backend/src/main/java/com/luxpretty/app/availability/web/AvailabilityController.java` | Modify | Public endpoint returns `List<SlotWithEmployees>` |
| `backend/src/test/java/com/luxpretty/app/bookings/app/EmployeeAssignmentServiceTests.java` | Create | Unit tests TC 15-17, 23-24 |
| `backend/src/test/java/com/luxpretty/app/availability/app/SlotAvailabilityServiceTests.java` | Modify | Add tests TC 13-14, 18-22 (fanned-out variant) |
| `backend/src/test/java/com/luxpretty/app/bookings/app/CareBookingServiceTests.java` | Modify | Add tests for qualification / leave / retry path |
| `backend/src/test/java/com/luxpretty/app/bookings/integration/BookingConcurrencyOracleTests.java` | Create | Testcontainers Oracle: TC 23 race condition |
| `frontend/src/app/features/bookings/models/bookings.model.ts` | Modify | Add `SlotWithEmployees`, `EmployeeSlotState` types |
| `frontend/src/app/features/bookings/services/bookings.service.ts` | Modify | New return type for availability |
| `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.ts` | Modify | Render per-employee chips, grey unavailable |
| `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.html` | Modify | Template for chips |
| `frontend/src/app/features/bookings/components/step-care/step-care.component.ts` | Modify | "Premier dispo" sends `employeeId=null`; specific employee filters |
| `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts` | Modify | Handle 409 errors with refresh |
| `frontend/src/assets/i18n/fr.json` | Modify | Add translation keys |
| `frontend/src/assets/i18n/en.json` | Modify | Add translation keys |

---

## Task 1: Add CANCELLATION_REASON column + field

**Files:**
- Create: `backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql` (partial — column add only in this task)
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/domain/CareBooking.java`
- Test: `backend/src/test/java/com/luxpretty/app/bookings/repo/CareBookingRepositoryTests.java` (modify or create)

- [ ] **Step 1: Write the failing repository test**

```java
// CareBookingRepositoryTests.java
@DataJpaTest
@ActiveProfiles("test")
class CareBookingRepositoryTests {

    @Autowired CareBookingRepository repo;

    @Test
    void persistsCancellationReason() {
        CareBooking b = new CareBooking();
        b.setStatus(CareBookingStatus.CANCELLED);
        b.setCancellationReason("LEGACY_NO_EMPLOYEE");
        b.setAppointmentDate(LocalDate.of(2026, 6, 1));
        b.setAppointmentTime(LocalTime.of(10, 0));
        // (minimal user/care setup elided — use fixture builder)
        CareBooking saved = repo.saveAndFlush(b);
        assertEquals("LEGACY_NO_EMPLOYEE", repo.findById(saved.getId()).orElseThrow().getCancellationReason());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=CareBookingRepositoryTests#persistsCancellationReason`
Expected: FAIL — `cancellationReason` field does not exist on `CareBooking`.

- [ ] **Step 3: Add the field to CareBooking entity**

In `CareBooking.java`, add (place near other simple columns):

```java
    @Column(name = "cancellation_reason", length = 64)
    private String cancellationReason;
```

- [ ] **Step 4: Create the Flyway migration file (column ADD only)**

Create `backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql` with just:

```sql
-- V7: Add CANCELLATION_REASON column. Schema and uniqueness changes in subsequent steps of this migration are added in later tasks of the plan.
ALTER TABLE CARE_BOOKINGS ADD CANCELLATION_REASON VARCHAR2(64);
```

> NOTE: Later tasks in this plan APPEND more SQL to this same V7 file. Do not split into multiple Flyway versions — Flyway runs each version once, and adding-then-modifying within a single feature branch is cleaner than V7/V8/V9 mini-migrations.

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -pl backend test -Dtest=CareBookingRepositoryTests#persistsCancellationReason`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql \
        backend/src/main/java/com/luxpretty/app/bookings/domain/CareBooking.java \
        backend/src/test/java/com/luxpretty/app/bookings/repo/CareBookingRepositoryTests.java
git commit -m "feat(booking): add CANCELLATION_REASON column to CARE_BOOKINGS"
```

---

## Task 2: Backfill NULL EMPLOYEE_ID on active bookings

**Files:**
- Modify: `backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql`
- Modify: `backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java`

- [ ] **Step 1: Append the backfill SQL to V7**

Append to `V7__booking_per_employee.sql`:

```sql
-- Backfill: assign legacy NULL employee_id on active bookings.
-- Default-self-employee migration (tenant V4) ensures every tenant has ≥ 1 active employee,
-- so the subquery returns a non-null id for normal tenants.
UPDATE CARE_BOOKINGS
   SET EMPLOYEE_ID = (
     SELECT MIN(e.ID) FROM EMPLOYEES e WHERE e.ACTIVE = 1
   )
 WHERE EMPLOYEE_ID IS NULL
   AND STATUS IN ('PENDING','CONFIRMED');

-- Safety net: any active bookings still NULL (tenant with zero active employees) -> cancelled
UPDATE CARE_BOOKINGS
   SET STATUS = 'CANCELLED', CANCELLATION_REASON = 'LEGACY_NO_EMPLOYEE'
 WHERE EMPLOYEE_ID IS NULL
   AND STATUS IN ('PENDING','CONFIRMED');
```

- [ ] **Step 2: Mirror in TenantSchemaMigrator for legacy tenants**

Open `backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java`. Add a new method `mirrorV7Backfill(String tenantSchema)` and call it from the existing schema-migration entrypoint (where other legacy mirrors live). Use this body:

```java
private void mirrorV7Backfill(String tenantSchema) {
    // Idempotent: ALTER TABLE ADD column wrapped in try/catch (ORA-01430 already exists)
    try {
        jdbcTemplate.execute("ALTER TABLE \"" + tenantSchema + "\".CARE_BOOKINGS ADD CANCELLATION_REASON VARCHAR2(64)");
    } catch (DataAccessException e) {
        if (!isAlreadyExists(e)) throw e;
    }
    jdbcTemplate.update(
        "UPDATE \"" + tenantSchema + "\".CARE_BOOKINGS " +
        "SET EMPLOYEE_ID = (SELECT MIN(e.ID) FROM \"" + tenantSchema + "\".EMPLOYEES e WHERE e.ACTIVE = 1) " +
        "WHERE EMPLOYEE_ID IS NULL AND STATUS IN ('PENDING','CONFIRMED')");
    jdbcTemplate.update(
        "UPDATE \"" + tenantSchema + "\".CARE_BOOKINGS " +
        "SET STATUS = 'CANCELLED', CANCELLATION_REASON = 'LEGACY_NO_EMPLOYEE' " +
        "WHERE EMPLOYEE_ID IS NULL AND STATUS IN ('PENDING','CONFIRMED')");
}

private boolean isAlreadyExists(DataAccessException e) {
    String msg = e.getMostSpecificCause().getMessage();
    return msg != null && (msg.contains("ORA-01430") || msg.contains("ORA-00955"));
}
```

(Adjust the entry point that loops over tenant schemas to call `mirrorV7Backfill(schema)`. Locate that loop — it likely already handles other migrations of the same shape; follow the existing pattern.)

- [ ] **Step 3: Run boot integration test (verify migration applies cleanly)**

Run: `mvn -pl backend test -Dtest=*ApplicationContextTests* -DfailIfNoTests=false`
Expected: PASS — context loads, V7 applies on H2 and on Testcontainers Oracle if configured.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql \
        backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java
git commit -m "feat(booking): backfill NULL EMPLOYEE_ID on active legacy bookings"
```

---

## Task 3: Drop UK_BOOKING_SLOT and create per-employee unique index

**Files:**
- Modify: `backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql`
- Modify: `backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java`

- [ ] **Step 1: Append schema changes to V7**

Append to `V7__booking_per_employee.sql`:

```sql
-- Drop the old global slot uniqueness (constraint dropped only after backfill).
ALTER TABLE CARE_BOOKINGS DROP CONSTRAINT UK_BOOKING_SLOT;

-- Per-employee uniqueness on active statuses (Oracle function-based unique index).
-- The CASE expressions return NULL for CANCELLED rows; Oracle excludes NULLs from
-- unique indexes, so cancelled rows do NOT block re-booking of the same slot.
CREATE UNIQUE INDEX UK_BOOKING_SLOT_EMPLOYEE ON CARE_BOOKINGS (
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_DATE END,
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_TIME END,
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN EMPLOYEE_ID END
);

-- Lookup performance for per-employee queries
CREATE INDEX IDX_BOOKING_DATE_EMPLOYEE ON CARE_BOOKINGS (APPOINTMENT_DATE, EMPLOYEE_ID);
```

- [ ] **Step 2: Extend TenantSchemaMigrator for legacy tenants**

Open `TenantSchemaMigrator.java` and extend `mirrorV7Backfill` with:

```java
// Drop the old constraint (idempotent: ORA-02443 = constraint does not exist)
try {
    jdbcTemplate.execute("ALTER TABLE \"" + tenantSchema + "\".CARE_BOOKINGS DROP CONSTRAINT UK_BOOKING_SLOT");
} catch (DataAccessException e) {
    if (!isMissingConstraint(e)) throw e;
}
// Create new unique index (idempotent: ORA-00955 = name already used)
try {
    jdbcTemplate.execute("CREATE UNIQUE INDEX \"" + tenantSchema + "\".UK_BOOKING_SLOT_EMPLOYEE ON \"" + tenantSchema + "\".CARE_BOOKINGS (" +
        "CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_DATE END," +
        "CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_TIME END," +
        "CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN EMPLOYEE_ID END)");
} catch (DataAccessException e) {
    if (!isAlreadyExists(e)) throw e;
}
try {
    jdbcTemplate.execute("CREATE INDEX \"" + tenantSchema + "\".IDX_BOOKING_DATE_EMPLOYEE ON \"" + tenantSchema + "\".CARE_BOOKINGS (APPOINTMENT_DATE, EMPLOYEE_ID)");
} catch (DataAccessException e) {
    if (!isAlreadyExists(e)) throw e;
}
```

And add `isMissingConstraint`:

```java
private boolean isMissingConstraint(DataAccessException e) {
    String msg = e.getMostSpecificCause().getMessage();
    return msg != null && msg.contains("ORA-02443");
}
```

- [ ] **Step 3: Run boot context test**

Run: `mvn -pl backend test -Dtest=LuxPrettyApplicationTests -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/V7__booking_per_employee.sql \
        backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaMigrator.java
git commit -m "feat(booking): per-employee unique index (UK_BOOKING_SLOT_EMPLOYEE)"
```

---

## Task 4: Add LeaveRequestRepository query for covering leaves

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/employee/repo/LeaveRequestRepository.java`
- Modify (or Create): `backend/src/test/java/com/luxpretty/app/employee/repo/LeaveRequestRepositoryTests.java`

- [ ] **Step 1: Write the failing test**

```java
// LeaveRequestRepositoryTests.java
@DataJpaTest
@ActiveProfiles("test")
class LeaveRequestRepositoryTests {

    @Autowired LeaveRequestRepository repo;
    @Autowired EmployeeRepository employees;

    @Test
    void findApprovedLeavesCovering_returnsOnlyApprovedLeavesThatCoverDate() {
        Employee marie = employees.save(buildEmployee("Marie"));
        Employee sophie = employees.save(buildEmployee("Sophie"));

        save(marie, LeaveStatus.APPROVED, LocalDate.of(2026,6,1), LocalDate.of(2026,6,3));
        save(sophie, LeaveStatus.PENDING,  LocalDate.of(2026,6,2), LocalDate.of(2026,6,2));
        save(sophie, LeaveStatus.APPROVED, LocalDate.of(2026,6,2), LocalDate.of(2026,6,2));

        List<LeaveRequest> covering = repo.findApprovedLeavesCovering(
            List.of(marie.getId(), sophie.getId()),
            LocalDate.of(2026,6,2));

        assertEquals(2, covering.size()); // Marie (range covers) + Sophie's APPROVED
        // PENDING leave excluded
    }

    private Employee buildEmployee(String name) { /* fixture, set name + active=true */ }
    private void save(Employee e, LeaveStatus s, LocalDate from, LocalDate to) {
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(e); lr.setStatus(s); lr.setStartDate(from); lr.setEndDate(to);
        lr.setType(LeaveType.PAID);
        repo.save(lr);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=LeaveRequestRepositoryTests#findApprovedLeavesCovering_returnsOnlyApprovedLeavesThatCoverDate`
Expected: FAIL — method `findApprovedLeavesCovering` does not exist.

- [ ] **Step 3: Add the query**

In `LeaveRequestRepository.java`:

```java
import com.luxpretty.app.employee.domain.LeaveStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;

@Query("SELECT lr FROM LeaveRequest lr " +
       "WHERE lr.status = com.luxpretty.app.employee.domain.LeaveStatus.APPROVED " +
       "AND lr.employee.id IN :employeeIds " +
       "AND lr.startDate <= :date AND lr.endDate >= :date")
List<LeaveRequest> findApprovedLeavesCovering(@Param("employeeIds") Collection<Long> employeeIds,
                                              @Param("date") LocalDate date);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/employee/repo/LeaveRequestRepository.java \
        backend/src/test/java/com/luxpretty/app/employee/repo/LeaveRequestRepositoryTests.java
git commit -m "feat(leave): add findApprovedLeavesCovering query"
```

---

## Task 5: Add CareBookingRepository queries for per-employee counts

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java`
- Modify: `backend/src/test/java/com/luxpretty/app/bookings/repo/CareBookingRepositoryTests.java`

- [ ] **Step 1: Write the failing tests**

Append:

```java
@Test
void countActiveByEmployeeAndDate_excludesCancelled() {
    Employee marie = persistEmployee("Marie");
    saveBooking(marie, LocalDate.of(2026,6,1), LocalTime.of(10,0), CareBookingStatus.CONFIRMED);
    saveBooking(marie, LocalDate.of(2026,6,1), LocalTime.of(11,0), CareBookingStatus.PENDING);
    saveBooking(marie, LocalDate.of(2026,6,1), LocalTime.of(12,0), CareBookingStatus.CANCELLED);

    Map<Long,Long> counts = repo.countActiveByEmployeeAndDate(LocalDate.of(2026,6,1)).stream()
        .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
    assertEquals(2L, counts.get(marie.getId()));
}

@Test
void findActiveByDateAndEmployees_returnsOnlyActiveStatuses() {
    Employee marie = persistEmployee("Marie");
    saveBooking(marie, LocalDate.of(2026,6,1), LocalTime.of(10,0), CareBookingStatus.CONFIRMED);
    saveBooking(marie, LocalDate.of(2026,6,1), LocalTime.of(11,0), CareBookingStatus.CANCELLED);
    List<CareBooking> rows = repo.findActiveByDateAndEmployees(
        LocalDate.of(2026,6,1), List.of(marie.getId()));
    assertEquals(1, rows.size());
    assertEquals(LocalTime.of(10,0), rows.get(0).getAppointmentTime());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl backend test -Dtest=CareBookingRepositoryTests#countActiveByEmployeeAndDate_excludesCancelled+CareBookingRepositoryTests#findActiveByDateAndEmployees_returnsOnlyActiveStatuses`
Expected: FAIL — methods missing.

- [ ] **Step 3: Add the queries**

In `CareBookingRepository.java`:

```java
@Query("SELECT b.employeeId, COUNT(b) FROM CareBooking b " +
       "WHERE b.appointmentDate = :date " +
       "AND b.status IN (com.luxpretty.app.bookings.domain.CareBookingStatus.PENDING, " +
       "                 com.luxpretty.app.bookings.domain.CareBookingStatus.CONFIRMED) " +
       "GROUP BY b.employeeId")
List<Object[]> countActiveByEmployeeAndDate(@Param("date") LocalDate date);

@Query("SELECT b FROM CareBooking b " +
       "WHERE b.appointmentDate = :date " +
       "AND b.employeeId IN :employeeIds " +
       "AND b.status IN (com.luxpretty.app.bookings.domain.CareBookingStatus.PENDING, " +
       "                 com.luxpretty.app.bookings.domain.CareBookingStatus.CONFIRMED)")
List<CareBooking> findActiveByDateAndEmployees(@Param("date") LocalDate date,
                                               @Param("employeeIds") Collection<Long> employeeIds);
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java \
        backend/src/test/java/com/luxpretty/app/bookings/repo/CareBookingRepositoryTests.java
git commit -m "feat(booking): repository queries for per-employee counts"
```

---

## Task 6: Create SlotWithEmployees and EmployeeSlotState DTOs

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/web/dto/EmployeeSlotState.java`
- Create: `backend/src/main/java/com/luxpretty/app/bookings/web/dto/SlotWithEmployees.java`

- [ ] **Step 1: Create EmployeeSlotState**

```java
package com.luxpretty.app.bookings.web.dto;

public record EmployeeSlotState(Long id, String name, boolean available, String reason) {
    public static EmployeeSlotState available(Long id, String name) {
        return new EmployeeSlotState(id, name, true, null);
    }
    public static EmployeeSlotState busy(Long id, String name) {
        return new EmployeeSlotState(id, name, false, "BUSY");
    }
    public static EmployeeSlotState onLeave(Long id, String name) {
        return new EmployeeSlotState(id, name, false, "ON_LEAVE");
    }
}
```

- [ ] **Step 2: Create SlotWithEmployees**

```java
package com.luxpretty.app.bookings.web.dto;

import java.util.List;

public record SlotWithEmployees(String time, List<EmployeeSlotState> employees) {}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl backend compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/web/dto/EmployeeSlotState.java \
        backend/src/main/java/com/luxpretty/app/bookings/web/dto/SlotWithEmployees.java
git commit -m "feat(booking): DTOs for per-employee slot state"
```

---

## Task 7: Add `getAvailableSlotsForCareWithEmployees` to SlotAvailabilityService

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/availability/app/SlotAvailabilityService.java`
- Modify: `backend/src/test/java/com/luxpretty/app/availability/app/SlotAvailabilityServiceTests.java`

This is the core read-side change. The new method orchestrates the existing per-employee slot computation across all qualified employees.

- [ ] **Step 1: Write failing tests (TC 13, 14, 18, 19, 20, 22)**

```java
// SlotAvailabilityServiceTests.java — add to existing test class
@Test
void perEmployeeSlots_marieBookedAt10_returnsSlotWithMarieGreyed() { // TC 13
    Employee marie = employee("Marie", true);
    Employee sophie = employee("Sophie", true);
    careAssignment(marie, careId); careAssignment(sophie, careId);
    booking(marie, date, LocalTime.of(10,0), CareBookingStatus.CONFIRMED);

    List<SlotWithEmployees> slots = service.getAvailableSlotsForCareWithEmployees(date, careId);
    SlotWithEmployees slot10 = slots.stream().filter(s -> s.time().equals("10:00")).findFirst().orElseThrow();
    assertEquals(2, slot10.employees().size());
    EmployeeSlotState marieState = slot10.employees().stream().filter(e -> e.id().equals(marie.getId())).findFirst().orElseThrow();
    assertFalse(marieState.available());
    assertEquals("BUSY", marieState.reason());
    EmployeeSlotState sophieState = slot10.employees().stream().filter(e -> e.id().equals(sophie.getId())).findFirst().orElseThrow();
    assertTrue(sophieState.available());
}

@Test
void perEmployeeSlots_allBooked_slotOmitted() { // TC 14
    Employee marie = employee("Marie", true);
    careAssignment(marie, careId);
    booking(marie, date, LocalTime.of(10,0), CareBookingStatus.CONFIRMED);
    List<SlotWithEmployees> slots = service.getAvailableSlotsForCareWithEmployees(date, careId);
    assertTrue(slots.stream().noneMatch(s -> s.time().equals("10:00")));
}

@Test
void perEmployeeSlots_strictQualification_excludesUnqualifiedEmployees() { // TC 18
    Employee marie = employee("Marie", true);   careAssignment(marie, careId);
    Employee sophie = employee("Sophie", true); // NOT assigned careId
    List<SlotWithEmployees> slots = service.getAvailableSlotsForCareWithEmployees(date, careId);
    assertTrue(slots.stream().allMatch(s -> s.employees().stream().allMatch(e -> e.id().equals(marie.getId()))));
}

@Test
void perEmployeeSlots_zeroQualifiedEmployees_returnsEmpty() { // TC 19
    Employee sophie = employee("Sophie", true); // no assignment
    List<SlotWithEmployees> slots = service.getAvailableSlotsForCareWithEmployees(date, careId);
    assertTrue(slots.isEmpty());
}

@Test
void perEmployeeSlots_employeeOnApprovedLeave_greyedAllDay() { // TC 20
    Employee sophie = employee("Sophie", true); careAssignment(sophie, careId);
    approvedLeave(sophie, date, date);
    List<SlotWithEmployees> slots = service.getAvailableSlotsForCareWithEmployees(date, careId);
    assertTrue(slots.stream().allMatch(s ->
        s.employees().stream().filter(e -> e.id().equals(sophie.getId()))
                              .allMatch(e -> "ON_LEAVE".equals(e.reason()))));
}

@Test
void perEmployeeSlots_allEmployeesOnLeave_noSlotReturned() { // TC 22
    Employee sophie = employee("Sophie", true); careAssignment(sophie, careId);
    approvedLeave(sophie, date, date);
    List<SlotWithEmployees> slots = service.getAvailableSlotsForCareWithEmployees(date, careId);
    assertTrue(slots.isEmpty());
}
```

(Test class needs fixture helpers `employee()`, `careAssignment()`, `booking()`, `approvedLeave()` — define them at the bottom of the test class using `TestEntityManager` if not already present.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl backend test -Dtest=SlotAvailabilityServiceTests`
Expected: FAIL — method `getAvailableSlotsForCareWithEmployees` does not exist.

- [ ] **Step 3: Implement the new method**

Add to `SlotAvailabilityService.java`:

```java
@Transactional(readOnly = true)
public List<SlotWithEmployees> getAvailableSlotsForCareWithEmployees(LocalDate date, Long careId) {
    if (date.isBefore(LocalDate.now())) return List.of();
    if (isClosedForHoliday(date)) return List.of();

    Care care = careRepo.findById(careId)
            .orElseThrow(() -> new ResourceNotFoundException("Care not found: " + careId));

    // Qualified active employees only (strict mode)
    List<Employee> qualified = employeeRepository.findByActiveTrueAndAssignedCares_Id(careId);
    if (qualified.isEmpty()) return List.of();

    List<Long> employeeIds = qualified.stream().map(Employee::getId).toList();

    // Approved leaves covering this date
    Set<Long> onLeaveIds = leaveRequestRepository.findApprovedLeavesCovering(employeeIds, date).stream()
            .map(lr -> lr.getEmployee().getId())
            .collect(Collectors.toSet());

    // Bookings of these employees on this date
    List<CareBooking> activeBookings = bookingRepo.findActiveByDateAndEmployees(date, employeeIds);

    // Compute candidate times once (salon hours, care duration, salon-wide blocks, holidays)
    // We piggy-back on the existing global computation by simulating one synthetic "no-employee"
    // window: salon hours only.
    List<TimeSlot> candidateTimes = computeSalonTimeWindows(date, care.getDuration());

    List<SlotWithEmployees> result = new ArrayList<>();
    int bufferMinutes = getBufferMinutes();
    for (TimeSlot slot : candidateTimes) {
        LocalTime slotStart = LocalTime.parse(slot.startTime());
        LocalTime slotEnd = LocalTime.parse(slot.endTime());

        List<EmployeeSlotState> per = qualified.stream().map(e -> {
            if (onLeaveIds.contains(e.getId())) return EmployeeSlotState.onLeave(e.getId(), e.getName());
            boolean busy = activeBookings.stream().anyMatch(b ->
                b.getEmployeeId() != null && b.getEmployeeId().equals(e.getId())
                && overlaps(slotStart, slotEnd, b.getAppointmentTime(),
                            b.getAppointmentTime().plusMinutes(b.getCare().getDuration() + bufferMinutes)));
            return busy ? EmployeeSlotState.busy(e.getId(), e.getName())
                        : EmployeeSlotState.available(e.getId(), e.getName());
        }).toList();

        if (per.stream().anyMatch(EmployeeSlotState::available)) {
            result.add(new SlotWithEmployees(slot.startTime(), per));
        }
    }
    return result;
}

// Helper: extract the existing slot-generation loop (lines ~108-129 of the current
// getAvailableSlots) into a private method returning the raw time grid.
private List<TimeSlot> computeSalonTimeWindows(LocalDate date, int careDuration) {
    int dow = date.getDayOfWeek().getValue();
    List<OpeningHour> openingHours = openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc().stream()
            .filter(oh -> oh.getDayOfWeek() == dow).toList();
    if (openingHours.isEmpty()) return List.of();

    List<BlockedSlot> blockedSlots = blockedSlotRepo
            .findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(date).stream()
            .filter(bs -> bs.getDate().equals(date)).toList();
    if (blockedSlots.stream().anyMatch(BlockedSlot::isFullDay)) return List.of();

    List<TimeSlot> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (OpeningHour oh : openingHours) {
        LocalTime cursor = oh.getOpenTime();
        LocalTime windowEnd = oh.getCloseTime();
        while (cursor.plusMinutes(careDuration).compareTo(windowEnd) <= 0) {
            LocalTime slotEnd = cursor.plusMinutes(careDuration);
            String key = cursor.toString();
            if (!seen.contains(key) && !isBlockedAt(cursor, slotEnd, blockedSlots)) {
                result.add(new TimeSlot(key, slotEnd.toString()));
            }
            seen.add(key);
            cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
        }
    }
    return result;
}

private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
    return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
}
```

Add the required imports at the top of the file:
```java
import com.luxpretty.app.bookings.web.dto.SlotWithEmployees;
import com.luxpretty.app.bookings.web.dto.EmployeeSlotState;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.repo.LeaveRequestRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
```

Add the new dependencies to the constructor:
```java
private final EmployeeRepository employeeRepository;
private final LeaveRequestRepository leaveRequestRepository;

// In constructor: add `EmployeeRepository employeeRepository, LeaveRequestRepository leaveRequestRepository`
// and assign.
```

(Also add to `EmployeeRepository`:
```java
List<Employee> findByActiveTrueAndAssignedCares_Id(Long careId);
```
if not present.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -pl backend test -Dtest=SlotAvailabilityServiceTests`
Expected: PASS (TC 13, 14, 18, 19, 20, 22).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/availability/app/SlotAvailabilityService.java \
        backend/src/main/java/com/luxpretty/app/employee/repo/EmployeeRepository.java \
        backend/src/test/java/com/luxpretty/app/availability/app/SlotAvailabilityServiceTests.java
git commit -m "feat(availability): per-employee slot fan-out for care"
```

---

## Task 8: Wire AvailabilityController to expose the new payload

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/availability/web/AvailabilityController.java`
- Modify: `backend/src/test/java/com/luxpretty/app/availability/web/AvailabilityControllerTests.java` (modify or create as `@WebMvcTest`)

- [ ] **Step 1: Write the failing controller test**

```java
@WebMvcTest(AvailabilityController.class)
class AvailabilityControllerTests {
    @Autowired MockMvc mvc;
    @MockBean SlotAvailabilityService service;

    @Test
    void availableSlotsByCare_returnsFanOutPayload() throws Exception {
        when(service.getAvailableSlotsForCareWithEmployees(LocalDate.of(2026,6,1), 7L))
            .thenReturn(List.of(new SlotWithEmployees("10:00",
                List.of(EmployeeSlotState.available(1L, "Marie"),
                        EmployeeSlotState.busy(2L, "Sophie")))));
        mvc.perform(get("/api/availability/slots/by-care")
                .param("date", "2026-06-01").param("careId", "7"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].time").value("10:00"))
           .andExpect(jsonPath("$[0].employees[0].name").value("Marie"))
           .andExpect(jsonPath("$[0].employees[0].available").value(true))
           .andExpect(jsonPath("$[0].employees[1].available").value(false))
           .andExpect(jsonPath("$[0].employees[1].reason").value("BUSY"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=AvailabilityControllerTests#availableSlotsByCare_returnsFanOutPayload`
Expected: FAIL — endpoint does not exist.

- [ ] **Step 3: Add the endpoint**

In `AvailabilityController.java`:

```java
@GetMapping("/slots/by-care")
public List<SlotWithEmployees> getAvailableSlotsByCare(
        @RequestParam LocalDate date,
        @RequestParam Long careId) {
    return slotAvailabilityService.getAvailableSlotsForCareWithEmployees(date, careId);
}
```

Add the import.

- [ ] **Step 4: Run the test to verify it passes**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/availability/web/AvailabilityController.java \
        backend/src/test/java/com/luxpretty/app/availability/web/AvailabilityControllerTests.java
git commit -m "feat(availability): expose /slots/by-care with per-employee fan-out"
```

---

## Task 9: Create EmployeeAssignmentService with least-loaded picker

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/app/EmployeeAssignmentService.java`
- Create: `backend/src/test/java/com/luxpretty/app/bookings/app/EmployeeAssignmentServiceTests.java`

- [ ] **Step 1: Write failing tests (TC 15, 16, 17)**

```java
package com.luxpretty.app.bookings.app;

@ExtendWith(MockitoExtension.class)
class EmployeeAssignmentServiceTests {

    @Mock EmployeeRepository employeeRepo;
    @Mock LeaveRequestRepository leaveRepo;
    @Mock CareBookingRepository bookingRepo;
    @InjectMocks EmployeeAssignmentService service;

    private final LocalDate date = LocalDate.of(2026,6,1);
    private final LocalTime time = LocalTime.of(10, 0);

    @Test
    void pickLeastLoaded_selectsEmployeeWithFewestBookingsThatDay() { // TC 15
        Employee marie = employee(1L, "Marie");
        Employee sophie = employee(2L, "Sophie");
        Employee lea = employee(3L, "Léa");
        when(employeeRepo.findByActiveTrueAndAssignedCares_Id(7L)).thenReturn(List.of(marie, sophie, lea));
        when(leaveRepo.findApprovedLeavesCovering(List.of(1L,2L,3L), date)).thenReturn(List.of());
        when(bookingRepo.findActiveByDateAndEmployees(date, List.of(1L,2L,3L))).thenReturn(List.of());
        when(bookingRepo.countActiveByEmployeeAndDate(date)).thenReturn(List.of(
            new Object[]{2L, 2L}, // Sophie 2
            new Object[]{3L, 3L}  // Léa 3 (Marie not in list = 0)
        ));
        Employee chosen = service.pickLeastLoaded(date, time, 7L);
        assertEquals(marie.getId(), chosen.getId());
    }

    @Test
    void pickLeastLoaded_alphaTieBreaker() { // TC 16
        Employee marie = employee(1L, "Marie");
        Employee sophie = employee(2L, "Sophie");
        when(employeeRepo.findByActiveTrueAndAssignedCares_Id(7L)).thenReturn(List.of(sophie, marie));
        when(leaveRepo.findApprovedLeavesCovering(anyList(), eq(date))).thenReturn(List.of());
        when(bookingRepo.findActiveByDateAndEmployees(date, List.of(2L,1L))).thenReturn(List.of());
        when(bookingRepo.countActiveByEmployeeAndDate(date)).thenReturn(List.of());
        Employee chosen = service.pickLeastLoaded(date, time, 7L);
        assertEquals("Marie", chosen.getName());
    }

    @Test
    void pickLeastLoaded_allBusyAtSlot_throwsNoEmployeeAvailable() { // TC 17
        Employee marie = employee(1L, "Marie");
        when(employeeRepo.findByActiveTrueAndAssignedCares_Id(7L)).thenReturn(List.of(marie));
        when(leaveRepo.findApprovedLeavesCovering(List.of(1L), date)).thenReturn(List.of());
        CareBooking existing = bookingAt(marie, time);
        when(bookingRepo.findActiveByDateAndEmployees(date, List.of(1L))).thenReturn(List.of(existing));
        assertThrows(NoEmployeeAvailableException.class,
            () -> service.pickLeastLoaded(date, time, 7L));
    }

    @Test
    void pickLeastLoaded_employeeOnLeave_excluded() {
        Employee marie = employee(1L, "Marie");
        Employee sophie = employee(2L, "Sophie");
        when(employeeRepo.findByActiveTrueAndAssignedCares_Id(7L)).thenReturn(List.of(marie, sophie));
        LeaveRequest leave = new LeaveRequest(); leave.setEmployee(marie); leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepo.findApprovedLeavesCovering(List.of(1L,2L), date)).thenReturn(List.of(leave));
        when(bookingRepo.findActiveByDateAndEmployees(date, List.of(1L,2L))).thenReturn(List.of());
        when(bookingRepo.countActiveByEmployeeAndDate(date)).thenReturn(List.of());
        Employee chosen = service.pickLeastLoaded(date, time, 7L);
        assertEquals(sophie.getId(), chosen.getId());
    }

    private Employee employee(Long id, String name) {
        Employee e = new Employee(); e.setId(id); e.setName(name); e.setActive(true);
        return e;
    }
    private CareBooking bookingAt(Employee e, LocalTime t) {
        CareBooking b = new CareBooking(); b.setEmployeeId(e.getId()); b.setAppointmentTime(t);
        Care care = new Care(); care.setDuration(30); b.setCare(care);
        return b;
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl backend test -Dtest=EmployeeAssignmentServiceTests`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the service**

```java
package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.repo.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class EmployeeAssignmentService {

    private final EmployeeRepository employeeRepo;
    private final LeaveRequestRepository leaveRepo;
    private final CareBookingRepository bookingRepo;

    public EmployeeAssignmentService(EmployeeRepository employeeRepo,
                                     LeaveRequestRepository leaveRepo,
                                     CareBookingRepository bookingRepo) {
        this.employeeRepo = employeeRepo;
        this.leaveRepo = leaveRepo;
        this.bookingRepo = bookingRepo;
    }

    @Transactional(readOnly = true)
    public Employee pickLeastLoaded(LocalDate date, LocalTime time, Long careId) {
        return pickLeastLoaded(date, time, careId, Set.of());
    }

    /** Same as {@link #pickLeastLoaded(LocalDate, LocalTime, Long)} but excludes employee ids
     *  already tried (used by the booking-create retry path after a UK collision). */
    @Transactional(readOnly = true)
    public Employee pickLeastLoaded(LocalDate date, LocalTime time, Long careId, Set<Long> excludeIds) {
        List<Employee> qualified = employeeRepo.findByActiveTrueAndAssignedCares_Id(careId);
        if (qualified.isEmpty()) throw new NoEmployeeAvailableException(careId, date, time);

        List<Long> ids = qualified.stream().map(Employee::getId).toList();
        Set<Long> onLeave = leaveRepo.findApprovedLeavesCovering(ids, date).stream()
            .map(lr -> lr.getEmployee().getId()).collect(java.util.stream.Collectors.toSet());

        List<CareBooking> dayBookings = bookingRepo.findActiveByDateAndEmployees(date, ids);

        Map<Long,Long> counts = new HashMap<>();
        bookingRepo.countActiveByEmployeeAndDate(date).forEach(row ->
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));

        List<Employee> candidates = qualified.stream()
            .filter(e -> !excludeIds.contains(e.getId()))
            .filter(e -> !onLeave.contains(e.getId()))
            .filter(e -> dayBookings.stream().noneMatch(b ->
                e.getId().equals(b.getEmployeeId()) && overlaps(time, b)))
            .sorted(Comparator
                .comparingLong((Employee e) -> counts.getOrDefault(e.getId(), 0L))
                .thenComparing(Employee::getName))
            .toList();

        if (candidates.isEmpty()) throw new NoEmployeeAvailableException(careId, date, time);
        return candidates.get(0);
    }

    private boolean overlaps(LocalTime requested, CareBooking b) {
        LocalTime bStart = b.getAppointmentTime();
        LocalTime bEnd = bStart.plusMinutes(b.getCare().getDuration());
        return requested.equals(bStart) || (requested.isAfter(bStart) && requested.isBefore(bEnd));
    }
}
```

And the exception:

```java
// backend/src/main/java/com/luxpretty/app/bookings/app/NoEmployeeAvailableException.java
package com.luxpretty.app.bookings.app;
import java.time.LocalDate;
import java.time.LocalTime;
public class NoEmployeeAvailableException extends RuntimeException {
    public final Long careId; public final LocalDate date; public final LocalTime time;
    public NoEmployeeAvailableException(Long careId, LocalDate date, LocalTime time) {
        super("No employee available for care " + careId + " at " + date + " " + time);
        this.careId = careId; this.date = date; this.time = time;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -pl backend test -Dtest=EmployeeAssignmentServiceTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/EmployeeAssignmentService.java \
        backend/src/main/java/com/luxpretty/app/bookings/app/NoEmployeeAvailableException.java \
        backend/src/test/java/com/luxpretty/app/bookings/app/EmployeeAssignmentServiceTests.java
git commit -m "feat(booking): EmployeeAssignmentService with least-loaded picker"
```

---

## Task 10: Create BookingErrorCodes constants

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/common/error/BookingErrorCodes.java`

- [ ] **Step 1: Create the constants class**

```java
package com.luxpretty.app.common.error;

public final class BookingErrorCodes {
    public static final String SLOT_TAKEN = "SLOT_TAKEN";
    public static final String NO_EMPLOYEE_AVAILABLE = "NO_EMPLOYEE_AVAILABLE";
    public static final String EMPLOYEE_NOT_QUALIFIED = "EMPLOYEE_NOT_QUALIFIED";
    public static final String EMPLOYEE_ON_LEAVE = "EMPLOYEE_ON_LEAVE";
    private BookingErrorCodes() {}
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl backend compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/common/error/BookingErrorCodes.java
git commit -m "feat(booking): error code constants"
```

---

## Task 11: Wire booking create() to use new assignment + qualification/leave checks

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java`
- Modify: `backend/src/test/java/com/luxpretty/app/bookings/app/CareBookingServiceTests.java`

- [ ] **Step 1: Write failing tests for the new behavior**

```java
@Test
void create_explicitEmployee_notQualified_throws400EmployeeNotQualified() {
    CareBookingRequest req = req(userId, careId, marie.getId(), date, LocalTime.of(10,0));
    when(employeeRepo.findById(marie.getId())).thenReturn(Optional.of(marie));
    marie.setAssignedCares(Set.of()); // not qualified for careId
    var ex = assertThrows(ResponseStatusException.class, () -> service.create(req));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains(BookingErrorCodes.EMPLOYEE_NOT_QUALIFIED));
}

@Test
void create_explicitEmployee_onLeave_throws409EmployeeOnLeave() {
    CareBookingRequest req = req(userId, careId, marie.getId(), date, LocalTime.of(10,0));
    marie.setAssignedCares(Set.of(care));
    when(employeeRepo.findById(marie.getId())).thenReturn(Optional.of(marie));
    when(leaveRequestService.isOnLeave(marie.getId(), date)).thenReturn(true);
    var ex = assertThrows(ResponseStatusException.class, () -> service.create(req));
    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    assertTrue(ex.getReason().contains(BookingErrorCodes.EMPLOYEE_ON_LEAVE));
}

@Test
void create_nullEmployee_autoAssignsLeastLoaded() {
    CareBookingRequest req = req(userId, careId, null, date, LocalTime.of(10,0));
    when(employeeAssignmentService.pickLeastLoaded(date, LocalTime.of(10,0), careId, Set.of()))
        .thenReturn(marie);
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    service.create(req);
    ArgumentCaptor<CareBooking> captor = ArgumentCaptor.forClass(CareBooking.class);
    verify(repo).save(captor.capture());
    assertEquals(marie.getId(), captor.getValue().getEmployeeId());
}

@Test
void create_dbCollision_returns409SlotTaken() {
    CareBookingRequest req = req(userId, careId, marie.getId(), date, LocalTime.of(10,0));
    marie.setAssignedCares(Set.of(care));
    when(employeeRepo.findById(marie.getId())).thenReturn(Optional.of(marie));
    when(leaveRequestService.isOnLeave(anyLong(), any())).thenReturn(false);
    when(repo.save(any())).thenThrow(new DataIntegrityViolationException("UK_BOOKING_SLOT_EMPLOYEE"));
    var ex = assertThrows(ResponseStatusException.class, () -> service.create(req));
    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    assertTrue(ex.getReason().contains(BookingErrorCodes.SLOT_TAKEN));
}

@Test
void create_nullEmployee_dbCollisionThenRetrySucceeds() {
    CareBookingRequest req = req(userId, careId, null, date, LocalTime.of(10,0));
    when(employeeAssignmentService.pickLeastLoaded(date, LocalTime.of(10,0), careId, Set.of()))
        .thenReturn(marie);
    when(employeeAssignmentService.pickLeastLoaded(date, LocalTime.of(10,0), careId, Set.of(marie.getId())))
        .thenReturn(sophie);
    when(repo.save(any()))
        .thenThrow(new DataIntegrityViolationException("UK_BOOKING_SLOT_EMPLOYEE"))
        .thenAnswer(inv -> inv.getArgument(0));
    CareBookingResponse resp = service.create(req);
    assertNotNull(resp);
    verify(repo, times(2)).save(any());
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -pl backend test -Dtest=CareBookingServiceTests#create_*`
Expected: FAIL — current behavior doesn't validate qualification/leave or use EmployeeAssignmentService.

- [ ] **Step 3: Refactor `create()`**

In `CareBookingService.java` replace the existing `create(CareBookingRequest req)` body (currently lines ~181-228) with:

```java
@Transactional
public CareBookingResponse create(CareBookingRequest req) {
    TenantContext.requireActive();
    var user = userRepository.findById(req.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.userId()));

    if (!Boolean.TRUE.equals(user.getEmailVerified())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
    }

    var care = careRepository.findById(req.careId())
            .orElseThrow(() -> new ResourceNotFoundException("Care not found: " + req.careId()));

    // Resolve employee: explicit (validate) or auto-assign (least-loaded)
    Employee employee = resolveEmployeeForBooking(req, care);

    // Insert with retry-once on collision when employeeId was auto-assigned
    return insertBookingWithRetry(req, user, care, employee, /*allowRetry*/ req.employeeId() == null);
}

private Employee resolveEmployeeForBooking(CareBookingRequest req, Care care) {
    if (req.employeeId() != null) {
        Employee e = employeeRepository.findById(req.employeeId())
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.employeeId()));
        if (!e.isActive() || e.getAssignedCares().stream().noneMatch(c -> c.getId().equals(care.getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                BookingErrorCodes.EMPLOYEE_NOT_QUALIFIED);
        }
        if (leaveRequestService.isOnLeave(e.getId(), req.appointmentDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                BookingErrorCodes.EMPLOYEE_ON_LEAVE);
        }
        return e;
    }
    try {
        return employeeAssignmentService.pickLeastLoaded(
            req.appointmentDate(), req.appointmentTime(), req.careId());
    } catch (NoEmployeeAvailableException nope) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            BookingErrorCodes.NO_EMPLOYEE_AVAILABLE);
    }
}

private CareBookingResponse insertBookingWithRetry(CareBookingRequest req, User user, Care care,
                                                    Employee employee, boolean allowRetry) {
    Set<Long> excluded = new HashSet<>();
    while (true) {
        // Stale CANCELLED row eviction is no longer needed: function-based UK excludes
        // cancelled rows via NULL semantics. (Keep removed.)
        CareBooking b = new CareBooking();
        b.setUser(user); b.setCare(care);
        CareBookingMapper.updateEntity(b, req);
        b.setEmployeeId(employee.getId()); // override with resolved/auto-assigned id
        if (req.salonClientId() != null) b.setSalonClientId(req.salonClientId());
        try {
            return CareBookingMapper.toResponse(repo.save(b));
        } catch (DataIntegrityViolationException ex) {
            if (!allowRetry) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, BookingErrorCodes.SLOT_TAKEN);
            }
            excluded.add(employee.getId());
            try {
                employee = employeeAssignmentService.pickLeastLoaded(
                    req.appointmentDate(), req.appointmentTime(), req.careId(), excluded);
            } catch (NoEmployeeAvailableException nope) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    BookingErrorCodes.NO_EMPLOYEE_AVAILABLE);
            }
            allowRetry = false; // one retry only
        }
    }
}
```

Inject the new dependencies (constructor):
```java
private final EmployeeRepository employeeRepository;
private final EmployeeAssignmentService employeeAssignmentService;
// add to constructor signature + assignment
```

Remove the call to `evictCancelledBookingsForSlot()` in `create()` (no longer needed — function-based UK excludes cancelled rows via Oracle NULL-not-stored-in-unique-index semantics).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -pl backend test -Dtest=CareBookingServiceTests`
Expected: PASS — including existing tests (verify no regression on `EMAIL_NOT_VERIFIED` path, etc.).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java \
        backend/src/test/java/com/luxpretty/app/bookings/app/CareBookingServiceTests.java
git commit -m "feat(booking): qualification + leave validation + auto-assign least-loaded"
```

---

## Task 12: Apply same logic to createClientBooking (public client path)

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java`
- Modify: `backend/src/test/java/com/luxpretty/app/bookings/app/CareBookingServiceTests.java`

`createClientBooking` is the path used by the public salon page (`/api/salon/{slug}/book`). It must use the same employee resolution logic.

- [ ] **Step 1: Write a failing test for the public path**

```java
@Test
void createClientBooking_nullEmployee_autoAssignsLeastLoaded() {
    when(employeeAssignmentService.pickLeastLoaded(any(), any(), eq(careId), eq(Set.of())))
        .thenReturn(marie);
    ClientBookingRequest req = clientReq(careId, null, date, LocalTime.of(10,0));
    // ... rest of setup
    service.createClientBooking(client, owner, "marie-salon", req);
    verify(repo).save(argThat(b -> marie.getId().equals(b.getEmployeeId())));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl backend test -Dtest=CareBookingServiceTests#createClientBooking_nullEmployee_autoAssignsLeastLoaded`
Expected: FAIL.

- [ ] **Step 3: Extract `resolveEmployee` helper and reuse it from both entry points**

Locate `createClientBooking(...)` (~line 315). Replace `resolveEmployeeForBooking(req, care)` from Task 11 with a more general helper signature so it can be called from both create paths:

```java
/** Shared employee resolution used by create() and createClientBooking(). */
private Employee resolveEmployee(LocalDate date, LocalTime time, Long careId,
                                 Long requestedEmployeeId, Care care) {
    if (requestedEmployeeId != null) {
        Employee e = employeeRepository.findById(requestedEmployeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + requestedEmployeeId));
        if (!e.isActive() || e.getAssignedCares().stream().noneMatch(c -> c.getId().equals(care.getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                BookingErrorCodes.EMPLOYEE_NOT_QUALIFIED);
        }
        if (leaveRequestService.isOnLeave(e.getId(), date)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                BookingErrorCodes.EMPLOYEE_ON_LEAVE);
        }
        return e;
    }
    try {
        return employeeAssignmentService.pickLeastLoaded(date, time, careId);
    } catch (NoEmployeeAvailableException nope) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            BookingErrorCodes.NO_EMPLOYEE_AVAILABLE);
    }
}
```

Update Task 11's `create()` to call `resolveEmployee(req.appointmentDate(), req.appointmentTime(), req.careId(), req.employeeId(), care)` (replacing the private `resolveEmployeeForBooking` from Task 11 — it was a stepping stone).

Update `createClientBooking()` to do the same:

```java
Employee employee = resolveEmployee(
    req.appointmentDate(), req.appointmentTime(), req.careId(),
    req.employeeId(), care);
```

Inside `createClientBooking`, replace the `repo.save(b)` with the same `insertBookingWithRetry` pattern from Task 11, passing `allowRetry = (req.employeeId() == null)`.

Remove `evictCancelledBookingsForSlot(...)` calls in `createClientBooking` (no longer needed — function-based UK excludes cancelled rows).

- [ ] **Step 4: Run all booking tests to confirm no regression**

Run: `mvn -pl backend test -Dtest=CareBookingServiceTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java \
        backend/src/test/java/com/luxpretty/app/bookings/app/CareBookingServiceTests.java
git commit -m "feat(booking): public createClientBooking uses same employee resolution"
```

---

## Task 13: Testcontainers Oracle smoke test for UK_BOOKING_SLOT_EMPLOYEE race

**Files:**
- Create: `backend/src/test/java/com/luxpretty/app/bookings/integration/BookingConcurrencyOracleTests.java`

> Memory: `project_pending_testcontainers_oracle.md` — H2 cannot reproduce Oracle function-based unique indexes. This test is required to verify TC 23 actually behaves under real Oracle semantics.

- [ ] **Step 1: Write the failing race-condition test**

```java
package com.luxpretty.app.bookings.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test-oracle")
class BookingConcurrencyOracleTests {

    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
        .withReuse(true);

    @DynamicPropertySource
    static void registerOracle(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
    }

    @Autowired CareBookingService service;
    @Autowired EmployeeRepository employees;
    // ... fixtures

    @Test
    void concurrent_bookings_same_employee_same_slot_oneWinsOneGets409SlotTaken() throws Exception {
        Employee sophie = employees.save(buildEmployee("Sophie", true, care));
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> errA = new AtomicReference<>();
        AtomicReference<Throwable> errB = new AtomicReference<>();

        Thread a = new Thread(() -> { try { start.await(); service.create(req(sophie.getId(), date, time)); } catch (Throwable t) { errA.set(t); }});
        Thread b = new Thread(() -> { try { start.await(); service.create(req(sophie.getId(), date, time)); } catch (Throwable t) { errB.set(t); }});

        a.start(); b.start();
        start.countDown();
        a.join(5000); b.join(5000);

        long failures = Stream.of(errA.get(), errB.get())
            .filter(Objects::nonNull)
            .filter(e -> e instanceof ResponseStatusException rse
                      && rse.getStatusCode() == HttpStatus.CONFLICT
                      && BookingErrorCodes.SLOT_TAKEN.equals(rse.getReason()))
            .count();
        long successes = 2 - failures;
        assertEquals(1, failures, "exactly one race-loser expected");
        assertEquals(1, successes, "exactly one race-winner expected");
    }

    @Test
    void cancelling_releases_slot_immediately() {
        Employee sophie = employees.save(buildEmployee("Sophie", true, care));
        CareBookingResponse first = service.create(req(sophie.getId(), date, time));
        service.cancel(first.id());
        // second insert at the same (date,time,sophie) must succeed because UK
        // excludes CANCELLED rows.
        assertDoesNotThrow(() -> service.create(req(sophie.getId(), date, time)));
    }
}
```

- [ ] **Step 2: Run the test (requires Docker)**

Run: `mvn -pl backend test -Dtest=BookingConcurrencyOracleTests`
Expected: PASS — first run will pull the Oracle image (slow, ~2-3 min); subsequent runs reuse the container.

If your dev box has no Docker available, mark the test `@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")` and document the CI requirement in the implementation plan completion notes.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/luxpretty/app/bookings/integration/BookingConcurrencyOracleTests.java
git commit -m "test(booking): Testcontainers Oracle smoke test for UK race + cancel-release"
```

---

## Task 14: Gate EmployeeLeaveController (placeholder — full gating in Chantier T)

> **Defer to Chantier T plan.** This task is a marker: the `@RequiresFeature(ABSENCE_MGMT)` annotation lives in Chantier T, so this gating happens during Chantier T's enforcement pass. No work here in Chantier B beyond confirming `EmployeeLeaveController` exists and is the right gating target.

- [ ] **Step 1: Verify file exists**

Run: `ls backend/src/main/java/com/luxpretty/app/employee/web/EmployeeLeaveController.java`
Expected: file present.

- [ ] **Step 2: Add a TODO note in the file header**

Add a one-line comment at the top of the class (it will be removed during Chantier T):

```java
// TODO(chantier-T): add @RequiresFeature(ABSENCE_MGMT) to all endpoints
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/employee/web/EmployeeLeaveController.java
git commit -m "chore(employee): TODO marker for Chantier T feature gating"
```

---

## Task 15: Frontend — update bookings model types

**Files:**
- Modify: `frontend/src/app/features/bookings/models/bookings.model.ts`

- [ ] **Step 1: Add the new types**

Append (or update existing slot type):

```typescript
export type EmployeeAvailabilityReason = 'BUSY' | 'ON_LEAVE';

export interface EmployeeSlotState {
  id: number;
  name: string;
  available: boolean;
  reason?: EmployeeAvailabilityReason;
}

export interface SlotWithEmployees {
  time: string; // "HH:mm"
  employees: EmployeeSlotState[];
}
```

- [ ] **Step 2: Verify TypeScript build**

Run: `cd frontend && npx tsc --noEmit`
Expected: SUCCESS (no errors from the new types alone).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/bookings/models/bookings.model.ts
git commit -m "feat(booking): frontend types for per-employee slot state"
```

---

## Task 16: Frontend — bookings service consumes new endpoint

**Files:**
- Modify: `frontend/src/app/features/bookings/services/bookings.service.ts`
- Modify: `frontend/src/app/features/bookings/services/bookings.service.spec.ts` (if exists, else create)

- [ ] **Step 1: Write the failing test**

```typescript
// bookings.service.spec.ts
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

describe('BookingsService.getAvailableSlotsByCare', () => {
  let service: BookingsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BookingsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookingsService);
    http = TestBed.inject(HttpTestingController);
  });

  it('GETs /api/availability/slots/by-care and returns SlotWithEmployees[]', () => {
    let result: SlotWithEmployees[] = [];
    service.getAvailableSlotsByCare('2026-06-01', 7).subscribe(r => result = r);
    const req = http.expectOne(r => r.url.endsWith('/api/availability/slots/by-care')
                                    && r.params.get('date') === '2026-06-01'
                                    && r.params.get('careId') === '7');
    req.flush([{ time: '10:00', employees: [{ id: 1, name: 'Marie', available: true }] }]);
    expect(result).toEqual([{ time: '10:00', employees: [{ id: 1, name: 'Marie', available: true }] }]);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npm test -- --include='**/bookings.service.spec.ts' --watch=false`
Expected: FAIL — method `getAvailableSlotsByCare` does not exist.

- [ ] **Step 3: Add the method**

In `bookings.service.ts`:

```typescript
import { SlotWithEmployees } from '../models/bookings.model';

getAvailableSlotsByCare(date: string, careId: number): Observable<SlotWithEmployees[]> {
  const params = new HttpParams().set('date', date).set('careId', String(careId));
  return this.http.get<SlotWithEmployees[]>(`${this.apiBase}/api/availability/slots/by-care`, { params });
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/bookings/services/bookings.service.ts \
        frontend/src/app/features/bookings/services/bookings.service.spec.ts
git commit -m "feat(booking): service method for per-employee availability"
```

---

## Task 17: Frontend — step-datetime renders per-employee chips

**Files:**
- Modify: `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.ts`
- Modify: `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.html`
- Modify: `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.scss`
- Modify: `frontend/src/app/features/bookings/components/step-datetime/step-datetime.component.spec.ts`

> First read the current `step-datetime.component.ts` to understand the existing inputs/outputs. Map the existing `slots: string[]` input to the new `slots: SlotWithEmployees[]` input.

- [ ] **Step 1: Write the failing component test**

```typescript
// step-datetime.component.spec.ts — add new it()
it('renders an employee chip per slot.employees entry with the .grey class when unavailable', () => {
  fixture.componentRef.setInput('slots', [{
    time: '10:00',
    employees: [
      { id: 1, name: 'Marie', available: true },
      { id: 2, name: 'Sophie', available: false, reason: 'BUSY' },
      { id: 3, name: 'Léa', available: false, reason: 'ON_LEAVE' },
    ],
  }]);
  fixture.detectChanges();
  const chips = fixture.nativeElement.querySelectorAll('[data-testid="employee-chip"]');
  expect(chips.length).toBe(3);
  expect(chips[0].classList.contains('grey')).toBeFalse();
  expect(chips[1].classList.contains('grey')).toBeTrue();
  expect(chips[2].classList.contains('grey')).toBeTrue();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npm test -- --include='**/step-datetime.component.spec.ts' --watch=false`
Expected: FAIL — no chips rendered yet.

- [ ] **Step 3: Update the component input type**

In `step-datetime.component.ts`, change the slots input from `string[]` to `SlotWithEmployees[]`. Emit a tuple `(time, employeeId)` instead of just `time` when the user clicks a chip:

```typescript
import { SlotWithEmployees, EmployeeSlotState } from '../../models/bookings.model';

slots = input.required<SlotWithEmployees[]>();
selected = output<{ time: string; employeeId: number }>();

onChipClick(slot: SlotWithEmployees, employee: EmployeeSlotState) {
  if (!employee.available) return;
  this.selected.emit({ time: slot.time, employeeId: employee.id });
}
```

- [ ] **Step 4: Update the template**

In `step-datetime.component.html`, replace the existing slot list with:

```html
@for (slot of slots(); track slot.time) {
  <div class="slot-row">
    <span class="slot-time">{{ slot.time }}</span>
    <div class="employee-chips">
      @for (emp of slot.employees; track emp.id) {
        <button
          type="button"
          data-testid="employee-chip"
          class="employee-chip"
          [class.grey]="!emp.available"
          [disabled]="!emp.available"
          [attr.title]="emp.available ? null : (emp.reason === 'ON_LEAVE' ? ('errors.booking.employeeOnLeave' | transloco) : ('errors.booking.employeeBusy' | transloco))"
          (click)="onChipClick(slot, emp)">
          {{ emp.name }}
        </button>
      }
    </div>
  </div>
}
```

- [ ] **Step 5: Style the chips**

In `step-datetime.component.scss`:

```scss
.slot-row { display: flex; gap: 12px; align-items: center; margin-bottom: 8px; }
.slot-time { font-weight: 500; min-width: 64px; }
.employee-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.employee-chip {
  border-radius: 9999px; padding: 4px 12px; border: 1px solid var(--mat-sys-outline);
  background: var(--mat-sys-surface); cursor: pointer; transition: opacity 150ms;
  &.grey { opacity: 0.4; cursor: not-allowed; }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: same command as Step 2.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/bookings/components/step-datetime/
git commit -m "feat(booking): step-datetime renders per-employee chips with greyed states"
```

---

## Task 18: Frontend — booking-stepper consumes the new selected event

**Files:**
- Modify: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`

- [ ] **Step 1: Update the slot-selection handler**

Find the existing handler that subscribes to `step-datetime`'s `selected` output. Change it from a `string` argument to `{ time: string; employeeId: number }`:

```typescript
onSlotSelected(payload: { time: string; employeeId: number }) {
  this.selectedTime.set(payload.time);
  this.selectedEmployeeId.set(payload.employeeId);
  this.goToNextStep();
}
```

If `selectedEmployeeId` does not exist yet on the stepper, add it:
```typescript
selectedEmployeeId = signal<number | null>(null);
```

And include `employeeId: this.selectedEmployeeId()` in the body sent to `POST /api/bookings`.

- [ ] **Step 2: Run all stepper tests**

Run: `cd frontend && npm test -- --include='**/booking-stepper.component.spec.ts' --watch=false`
Expected: PASS (existing tests adapted if needed).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/bookings/components/booking-stepper/
git commit -m "feat(booking): stepper carries employeeId through to POST /bookings"
```

---

## Task 19: Frontend — handle 409 SLOT_TAKEN / NO_EMPLOYEE_AVAILABLE

**Files:**
- Modify: `frontend/src/app/features/bookings/components/booking-stepper/booking-stepper.component.ts`
- Modify: `frontend/src/assets/i18n/fr.json`
- Modify: `frontend/src/assets/i18n/en.json`

- [ ] **Step 1: Add translation keys**

In `fr.json` add:

```json
{
  "errors": {
    "booking": {
      "slotTaken": "Ce créneau vient d'être pris, veuillez en choisir un autre.",
      "noEmployeeAvailable": "Aucun praticien disponible pour ce créneau.",
      "employeeBusy": "Praticien occupé",
      "employeeOnLeave": "Praticien en congé",
      "employeeNotQualified": "Ce praticien ne réalise pas ce soin.",
      "employeeOnLeaveBlock": "Ce praticien est en congé à cette date."
    }
  }
}
```

In `en.json` add:

```json
{
  "errors": {
    "booking": {
      "slotTaken": "This slot was just taken — please choose another.",
      "noEmployeeAvailable": "No practitioner is available for this slot.",
      "employeeBusy": "Practitioner busy",
      "employeeOnLeave": "Practitioner on leave",
      "employeeNotQualified": "This practitioner does not offer this service.",
      "employeeOnLeaveBlock": "This practitioner is on leave on this date."
    }
  }
}
```

(Merge into existing structure if keys already partially exist.)

- [ ] **Step 2: Handle 409 in booking-stepper**

In the `submitBooking` flow, on `HttpErrorResponse` add:

```typescript
catchError((err: HttpErrorResponse) => {
  if (err.status === 409 && err.error?.error === 'SLOT_TAKEN') {
    this.snackBar.open(this.transloco.translate('errors.booking.slotTaken'), 'OK', { duration: 4000 });
    this.refreshSlots(); // reload availability
    this.currentStep.set('datetime');
    return EMPTY;
  }
  if (err.status === 409 && err.error?.error === 'NO_EMPLOYEE_AVAILABLE') {
    this.snackBar.open(this.transloco.translate('errors.booking.noEmployeeAvailable'), 'OK', { duration: 4000 });
    this.refreshSlots();
    this.currentStep.set('datetime');
    return EMPTY;
  }
  return throwError(() => err);
}),
```

(Adjust to match the actual error envelope: backend currently uses `ResponseStatusException` with reason as the code, so `err.error` may be the raw text. Inspect a real response in dev tools and align — if needed, switch to checking `err.error?.includes('SLOT_TAKEN')`.)

- [ ] **Step 3: Run the test suite**

Run: `cd frontend && npm test -- --include='**/booking-stepper*' --watch=false`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/bookings/components/booking-stepper/ \
        frontend/src/assets/i18n/fr.json \
        frontend/src/assets/i18n/en.json
git commit -m "feat(booking): handle 409 SLOT_TAKEN / NO_EMPLOYEE_AVAILABLE with refresh"
```

---

## Task 20: Manual E2E verification

- [ ] **Step 1: Start backend + frontend**

Backend: `cd backend && mvn spring-boot:run`
Frontend: `docker compose --profile dev up frontend-dev`

- [ ] **Step 2: Verify multi-employee scenario manually**

In the browser at `http://localhost:4300`:

1. Log in as a pro tenant with ≥ 2 employees both qualified for the same care.
2. Open the public salon page → book the same time slot as employee A.
3. From a second browser/incognito, repeat at the same time slot with employee B → should succeed (no global lock).
4. From a third tab, book the same time slot with "Premier disponible" → backend auto-assigns, succeeds.
5. From a fourth tab, attempt the same time slot with "Premier disponible" → expect 409 `NO_EMPLOYEE_AVAILABLE` toast.

- [ ] **Step 3: Verify cancel-release**

Cancel one of the bookings → re-book the same slot/employee → should succeed.

- [ ] **Step 4: Capture screenshots and confirm**

Document the result in the PR description.

---

## Self-Review Checklist

After completing all tasks, verify:

- [ ] **Spec coverage:** Every test case TC 13-27 in spec §4.6 maps to at least one Task above.
  - TC 13 → Task 7 (`perEmployeeSlots_marieBookedAt10_returnsSlotWithMarieGreyed`)
  - TC 14 → Task 7 (`perEmployeeSlots_allBooked_slotOmitted`)
  - TC 15-17 → Task 9 (`pickLeastLoaded_*`)
  - TC 18-19 → Task 7
  - TC 20-22 → Task 7
  - TC 23, 25 → Task 13 (Testcontainers Oracle)
  - TC 24 → Task 11 (`create_nullEmployee_dbCollisionThenRetrySucceeds`)
  - TC 26 → Task 13 (`cancelling_releases_slot_immediately`)
  - TC 27 → Task 2 (backfill in V7)

- [ ] **Type consistency:** `SlotWithEmployees`, `EmployeeSlotState` types match between backend DTOs and frontend models.

- [ ] **No `evictCancelledBookingsForSlot` calls remain** in `create()` or `createClientBooking()` — function-based UK makes this obsolete.

- [ ] **Migration applies cleanly** on H2 (test profile) and on Testcontainers Oracle (integration).

- [ ] **No legacy frontend code paths** still call the old `/available-slots` flat endpoint for the new flow (the old endpoint stays for backward compatibility but new code uses `/slots/by-care`).
