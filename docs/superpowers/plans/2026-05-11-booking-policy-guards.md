# Booking Policy Guards (PR1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-tenant booking policy with two configurable limits (max bookings/day per client, max bookings/week for new client) enforced server-side, configurable from a new tab in `/pro/availability`, surfaced as 409 with typed error codes consumed by the client booking flow.

**Architecture:** Singleton-per-tenant `BookingPolicy` JPA entity (one row per tenant schema) with a dedicated `BookingPolicyService` enforcing limits inside `CareBookingService.createClientBooking(...)` before INSERT. New `/api/pro/booking-policy` GET/PUT endpoints. New Angular component `BookingPolicyComponent` provided as a tab inside `/pro/availability`. Client booking flow catches the typed 409 codes and shows localized messages.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JPA / Flyway / Oracle (per-tenant schema). Angular 20 standalone + NgRx SignalStore + Angular Material + Transloco i18n. JUnit 5 backend, Karma/Jasmine frontend.

**Reference spec:** `docs/superpowers/specs/2026-05-11-booking-policy-guards-design.md`

---

## File Structure

**Backend (create):**
- `backend/src/main/java/com/luxpretty/app/bookings/domain/BookingPolicy.java`
- `backend/src/main/java/com/luxpretty/app/bookings/repo/BookingPolicyRepository.java`
- `backend/src/main/java/com/luxpretty/app/bookings/app/BookingPolicyService.java`
- `backend/src/main/java/com/luxpretty/app/bookings/web/BookingPolicyController.java`
- `backend/src/main/java/com/luxpretty/app/bookings/web/dto/UpdateBookingPolicyRequest.java`
- `backend/src/main/java/com/luxpretty/app/bookings/web/dto/BookingPolicyResponse.java`
- `backend/src/main/java/com/luxpretty/app/bookings/web/mapper/BookingPolicyMapper.java`
- `backend/src/main/java/com/luxpretty/app/common/error/BookingLimitExceededException.java`
- `backend/src/main/resources/db/migration/tenant/V2__create_booking_policy.sql`

**Backend (modify):**
- `backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java` — add 3 query methods.
- `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java` — call `BookingPolicyService.validateClientLimits(...)` inside `createClientBooking(...)`.
- `backend/src/main/java/com/luxpretty/app/common/error/GlobalExceptionHandler.java` — add `BookingLimitExceededException` handler.

**Backend (tests):**
- `backend/src/test/java/com/luxpretty/app/bookings/app/BookingPolicyServiceTests.java`
- `backend/src/test/java/com/luxpretty/app/bookings/web/BookingPolicyControllerTests.java`
- `backend/src/test/java/com/luxpretty/app/bookings/app/BookingServiceLimitsIntegrationTests.java`

**Frontend (create):**
- `frontend/src/app/features/availability/booking-policy/booking-policy.component.ts`
- `frontend/src/app/features/availability/booking-policy/booking-policy.component.html`
- `frontend/src/app/features/availability/booking-policy/booking-policy.component.scss`
- `frontend/src/app/features/availability/booking-policy/booking-policy.component.spec.ts`
- `frontend/src/app/features/availability/booking-policy/booking-policy.service.ts`
- `frontend/src/app/features/availability/booking-policy/booking-policy.service.spec.ts`
- `frontend/src/app/features/availability/booking-policy/booking-policy.store.ts`
- `frontend/src/app/features/availability/booking-policy/booking-policy.store.spec.ts`
- `frontend/src/app/features/availability/booking-policy/booking-policy.model.ts`

**Frontend (modify):**
- `frontend/src/app/features/availability/availability.component.html` — wrap content in `mat-tab-group` with 2 tabs.
- `frontend/src/app/features/availability/availability.component.ts` — import MatTabsModule + new component.
- `frontend/src/app/features/availability/availability.component.spec.ts` — assert 2 tabs render.
- `frontend/src/app/features/bookings/services/<bookings.service>.ts` — extend the booking POST error handling (file path confirmed in Task 14).
- `frontend/src/app/features/bookings/components/<modal-or-stepper>.spec.ts` — booking flow 409 handling tests (file path confirmed in Task 15).
- `frontend/public/i18n/fr.json` — add `pro.bookingPolicy.*` and `errors.booking.limit*`.
- `frontend/public/i18n/en.json` — same keys, English values.

---

## Task 1: Domain entity `BookingPolicy`

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/domain/BookingPolicy.java`

- [ ] **Step 1: Create the entity file**

```java
package com.luxpretty.app.bookings.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BOOKING_POLICY")
public class BookingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_bookings_per_day_per_client", nullable = false)
    private Integer maxBookingsPerDayPerClient;

    @Column(name = "max_bookings_per_week_for_new_client", nullable = false)
    private Integer maxBookingsPerWeekForNewClient;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd backend && mvn -q -pl . -DskipTests compile`
Expected: BUILD SUCCESS, no warnings on the new file.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/domain/BookingPolicy.java
git commit -m "feat(booking-policy): add BookingPolicy domain entity"
```

---

## Task 2: Repository `BookingPolicyRepository`

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/repo/BookingPolicyRepository.java`

- [ ] **Step 1: Create the repo**

```java
package com.luxpretty.app.bookings.repo;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingPolicyRepository extends JpaRepository<BookingPolicy, Long> {

    /**
     * Singleton-per-tenant: returns the single BookingPolicy row of the current
     * tenant schema, or empty if the row does not exist yet.
     */
    Optional<BookingPolicy> findFirstByOrderByIdAsc();
}
```

- [ ] **Step 2: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/repo/BookingPolicyRepository.java
git commit -m "feat(booking-policy): add BookingPolicyRepository"
```

---

## Task 3: Add count queries on `CareBookingRepository`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java`

- [ ] **Step 1: Append the 3 query methods**

Add these inside the `CareBookingRepository` interface, just before the closing `}`:

```java
    /**
     * Count "live" bookings (PENDING/CONFIRMED) for a user on a given day.
     * Used by the daily-limit check in BookingPolicyService.
     */
    long countByUserIdAndAppointmentDateAndStatusIn(
        Long userId,
        LocalDate date,
        Collection<CareBookingStatus> statuses
    );

    /**
     * Count "live" bookings (PENDING/CONFIRMED) for a user inside a date range.
     * Used by the new-client weekly-limit check.
     */
    long countByUserIdAndAppointmentDateBetweenAndStatusIn(
        Long userId,
        LocalDate from,
        LocalDate to,
        Collection<CareBookingStatus> statuses
    );

    /**
     * True if the user has at least one CONFIRMED booking strictly in the past.
     * Used to identify whether a client is "new" to this salon (false → new).
     */
    boolean existsByUserIdAndStatusAndAppointmentDateBefore(
        Long userId,
        CareBookingStatus status,
        LocalDate today
    );
```

- [ ] **Step 2: Add the missing import**

At the top of the file, add `import java.util.Collection;` next to the existing `import java.util.List;`.

- [ ] **Step 3: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/repo/CareBookingRepository.java
git commit -m "feat(booking-policy): add count queries for client booking limits"
```

---

## Task 4: Custom exception `BookingLimitExceededException`

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/common/error/BookingLimitExceededException.java`

- [ ] **Step 1: Create the exception**

```java
package com.luxpretty.app.common.error;

import lombok.Getter;

/**
 * Thrown when a client tries to book more appointments than the tenant's
 * BookingPolicy allows. Mapped to HTTP 409 with a typed error body by
 * GlobalExceptionHandler so the frontend can localize the message based on
 * {@link #code}.
 */
@Getter
public class BookingLimitExceededException extends RuntimeException {

    public static final String CODE_DAILY = "BOOKING_LIMIT_DAILY_EXCEEDED";
    public static final String CODE_NEW_CLIENT_WEEKLY = "BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED";

    private final String code;
    private final int limit;
    private final int currentCount;

    public BookingLimitExceededException(String code, int limit, int currentCount) {
        super(code + " (limit=" + limit + ", current=" + currentCount + ")");
        this.code = code;
        this.limit = limit;
        this.currentCount = currentCount;
    }
}
```

- [ ] **Step 2: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/common/error/BookingLimitExceededException.java
git commit -m "feat(booking-policy): add BookingLimitExceededException with typed codes"
```

---

## Task 5: Wire the exception into `GlobalExceptionHandler`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/common/error/GlobalExceptionHandler.java`

- [ ] **Step 1: Add the handler**

Inside the class body, append this method (before the closing `}`):

```java
    @ExceptionHandler(BookingLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> bookingLimitExceeded(BookingLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "code", ex.getCode(),
                        "message", ex.getMessage(),
                        "limit", ex.getLimit(),
                        "currentCount", ex.getCurrentCount()
                ));
    }
```

- [ ] **Step 2: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/common/error/GlobalExceptionHandler.java
git commit -m "feat(booking-policy): map BookingLimitExceededException to HTTP 409"
```

---

## Task 6: Service `BookingPolicyService` (TDD)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/app/BookingPolicyService.java`
- Test: `backend/src/test/java/com/luxpretty/app/bookings/app/BookingPolicyServiceTests.java`

- [ ] **Step 1: Write the failing test file**

```java
package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.BookingPolicyRepository;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.common.error.BookingLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingPolicyServiceTests {

    private BookingPolicyRepository policyRepo;
    private CareBookingRepository bookingRepo;
    private BookingPolicyService service;

    private static final Long USER_ID = 42L;
    private static final LocalDate TUESDAY = LocalDate.of(2026, 5, 12); // a Tuesday
    private static final List<CareBookingStatus> LIVE = List.of(
            CareBookingStatus.PENDING, CareBookingStatus.CONFIRMED);

    @BeforeEach
    void setUp() {
        policyRepo = mock(BookingPolicyRepository.class);
        bookingRepo = mock(CareBookingRepository.class);
        service = new BookingPolicyService(policyRepo, bookingRepo);

        BookingPolicy defaultPolicy = new BookingPolicy();
        defaultPolicy.setMaxBookingsPerDayPerClient(1);
        defaultPolicy.setMaxBookingsPerWeekForNewClient(1);
        when(policyRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultPolicy));
    }

    @Test
    void allowsBookingWhenDailyCountUnderLimit() {
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(eq(USER_ID), eq(TUESDAY), any()))
                .thenReturn(0L);
        when(bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(
                eq(USER_ID), eq(CareBookingStatus.CONFIRMED), any()))
                .thenReturn(true); // existing client → new-client check skipped

        assertThatCode(() -> service.validateClientLimits(USER_ID, TUESDAY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBookingWhenDailyLimitReached() {
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(eq(USER_ID), eq(TUESDAY), any()))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.validateClientLimits(USER_ID, TUESDAY))
                .isInstanceOfSatisfying(BookingLimitExceededException.class, ex -> {
                    assertThat(ex.getCode())
                            .isEqualTo(BookingLimitExceededException.CODE_DAILY);
                    assertThat(ex.getLimit()).isEqualTo(1);
                    assertThat(ex.getCurrentCount()).isEqualTo(1);
                });
    }

    @Test
    void allowsExistingClientUnconstrainedByWeeklyRule() {
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(eq(USER_ID), eq(TUESDAY), any()))
                .thenReturn(0L);
        when(bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(
                eq(USER_ID), eq(CareBookingStatus.CONFIRMED), any()))
                .thenReturn(true);
        // Even with many bookings this week, existing client is not constrained
        when(bookingRepo.countByUserIdAndAppointmentDateBetweenAndStatusIn(
                eq(USER_ID), any(), any(), any()))
                .thenReturn(5L);

        assertThatCode(() -> service.validateClientLimits(USER_ID, TUESDAY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNewClientWhenWeeklyLimitReached() {
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(eq(USER_ID), eq(TUESDAY), any()))
                .thenReturn(0L);
        when(bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(
                eq(USER_ID), eq(CareBookingStatus.CONFIRMED), any()))
                .thenReturn(false); // never came before
        // Monday 2026-05-11 → Sunday 2026-05-17
        when(bookingRepo.countByUserIdAndAppointmentDateBetweenAndStatusIn(
                eq(USER_ID),
                eq(LocalDate.of(2026, 5, 11)),
                eq(LocalDate.of(2026, 5, 17)),
                any()))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.validateClientLimits(USER_ID, TUESDAY))
                .isInstanceOfSatisfying(BookingLimitExceededException.class, ex -> {
                    assertThat(ex.getCode())
                            .isEqualTo(BookingLimitExceededException.CODE_NEW_CLIENT_WEEKLY);
                    assertThat(ex.getLimit()).isEqualTo(1);
                    assertThat(ex.getCurrentCount()).isEqualTo(1);
                });
    }

    @Test
    void allowsNewClientFirstBookingOfTheWeek() {
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(eq(USER_ID), eq(TUESDAY), any()))
                .thenReturn(0L);
        when(bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(
                eq(USER_ID), eq(CareBookingStatus.CONFIRMED), any()))
                .thenReturn(false);
        when(bookingRepo.countByUserIdAndAppointmentDateBetweenAndStatusIn(
                eq(USER_ID), any(), any(), any()))
                .thenReturn(0L);

        assertThatCode(() -> service.validateClientLimits(USER_ID, TUESDAY))
                .doesNotThrowAnyException();
    }

    @Test
    void weekBoundaryRespectsIsoWeek() {
        // Sunday 2026-05-17 belongs to week starting Monday 2026-05-11
        // Monday 2026-05-18 belongs to the NEXT week starting Monday 2026-05-18
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(any(), any(), any())).thenReturn(0L);
        when(bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(any(), any(), any())).thenReturn(false);
        // Booking Monday 2026-05-18: weekly window must be 2026-05-18 → 2026-05-24
        when(bookingRepo.countByUserIdAndAppointmentDateBetweenAndStatusIn(
                any(),
                eq(LocalDate.of(2026, 5, 18)),
                eq(LocalDate.of(2026, 5, 24)),
                any()))
                .thenReturn(0L);

        assertThatCode(() -> service.validateClientLimits(USER_ID, LocalDate.of(2026, 5, 18)))
                .doesNotThrowAnyException();
    }

    @Test
    void createsDefaultPolicyWhenRowMissing() {
        when(policyRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(policyRepo.save(any(BookingPolicy.class))).thenAnswer(inv -> {
            BookingPolicy saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(any(), any(), any())).thenReturn(0L);
        when(bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(any(), any(), any())).thenReturn(true);

        BookingPolicy result = service.getOrCreatePolicy();

        assertThat(result.getMaxBookingsPerDayPerClient()).isEqualTo(1);
        assertThat(result.getMaxBookingsPerWeekForNewClient()).isEqualTo(1);
    }

    @Test
    void cancelledAndNoShowBookingsExcludedFromLiveCount() {
        // Service must pass [PENDING, CONFIRMED] only; we assert via the captured Collection
        when(bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(
                eq(USER_ID), eq(TUESDAY), eq((Collection<CareBookingStatus>) LIVE)))
                .thenReturn(0L);
        when(bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(any(), any(), any())).thenReturn(true);

        assertThatCode(() -> service.validateClientLimits(USER_ID, TUESDAY))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests, expect failure**

Run: `cd backend && mvn -q -Dtest=BookingPolicyServiceTests test`
Expected: COMPILATION ERROR (`BookingPolicyService` does not exist).

- [ ] **Step 3: Implement the service**

```java
package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.BookingPolicyRepository;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.common.error.BookingLimitExceededException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enforces per-tenant client booking limits before a CareBooking is persisted.
 * The policy is a singleton row in BOOKING_POLICY (per-tenant schema). If the
 * row is missing for a legacy tenant, default values (1/1) are auto-created on
 * first read.
 */
@Service
public class BookingPolicyService {

    private static final List<CareBookingStatus> LIVE_STATUSES = List.of(
            CareBookingStatus.PENDING, CareBookingStatus.CONFIRMED);

    private static final int DEFAULT_MAX_PER_DAY = 1;
    private static final int DEFAULT_MAX_PER_WEEK_NEW_CLIENT = 1;

    private final BookingPolicyRepository policyRepo;
    private final CareBookingRepository bookingRepo;

    public BookingPolicyService(BookingPolicyRepository policyRepo, CareBookingRepository bookingRepo) {
        this.policyRepo = policyRepo;
        this.bookingRepo = bookingRepo;
    }

    @Transactional
    public BookingPolicy getOrCreatePolicy() {
        return policyRepo.findFirstByOrderByIdAsc().orElseGet(() -> {
            BookingPolicy fresh = new BookingPolicy();
            fresh.setMaxBookingsPerDayPerClient(DEFAULT_MAX_PER_DAY);
            fresh.setMaxBookingsPerWeekForNewClient(DEFAULT_MAX_PER_WEEK_NEW_CLIENT);
            fresh.setUpdatedAt(LocalDateTime.now());
            return policyRepo.save(fresh);
        });
    }

    @Transactional
    public BookingPolicy update(int maxPerDay, int maxPerWeekNewClient) {
        BookingPolicy current = getOrCreatePolicy();
        current.setMaxBookingsPerDayPerClient(maxPerDay);
        current.setMaxBookingsPerWeekForNewClient(maxPerWeekNewClient);
        current.setUpdatedAt(LocalDateTime.now());
        return policyRepo.save(current);
    }

    /**
     * Throws BookingLimitExceededException if either daily or weekly-for-new-client
     * limit is breached. Caller is expected to invoke this BEFORE saving the new
     * CareBooking, inside the same transaction as the booking insert.
     */
    @Transactional(readOnly = true)
    public void validateClientLimits(Long userId, LocalDate appointmentDate) {
        BookingPolicy policy = getOrCreatePolicy();

        long dailyCount = bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(
                userId, appointmentDate, LIVE_STATUSES);
        if (dailyCount >= policy.getMaxBookingsPerDayPerClient()) {
            throw new BookingLimitExceededException(
                    BookingLimitExceededException.CODE_DAILY,
                    policy.getMaxBookingsPerDayPerClient(),
                    (int) dailyCount);
        }

        boolean isExistingClient = bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(
                userId, CareBookingStatus.CONFIRMED, LocalDate.now());
        if (!isExistingClient) {
            LocalDate weekStart = appointmentDate.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            long weeklyCount = bookingRepo.countByUserIdAndAppointmentDateBetweenAndStatusIn(
                    userId, weekStart, weekEnd, LIVE_STATUSES);
            if (weeklyCount >= policy.getMaxBookingsPerWeekForNewClient()) {
                throw new BookingLimitExceededException(
                        BookingLimitExceededException.CODE_NEW_CLIENT_WEEKLY,
                        policy.getMaxBookingsPerWeekForNewClient(),
                        (int) weeklyCount);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests, expect green**

Run: `cd backend && mvn -q -Dtest=BookingPolicyServiceTests test`
Expected: `Tests run: 8, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/BookingPolicyService.java \
        backend/src/test/java/com/luxpretty/app/bookings/app/BookingPolicyServiceTests.java
git commit -m "feat(booking-policy): add BookingPolicyService with limit enforcement"
```

---

## Task 7: DTOs + mapper

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/web/dto/UpdateBookingPolicyRequest.java`
- Create: `backend/src/main/java/com/luxpretty/app/bookings/web/dto/BookingPolicyResponse.java`
- Create: `backend/src/main/java/com/luxpretty/app/bookings/web/mapper/BookingPolicyMapper.java`

- [ ] **Step 1: Create the request DTO**

```java
package com.luxpretty.app.bookings.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateBookingPolicyRequest(
        @NotNull @Min(1) @Max(10) Integer maxBookingsPerDayPerClient,
        @NotNull @Min(1) @Max(10) Integer maxBookingsPerWeekForNewClient
) {}
```

- [ ] **Step 2: Create the response DTO**

```java
package com.luxpretty.app.bookings.web.dto;

import java.time.LocalDateTime;

public record BookingPolicyResponse(
        Integer maxBookingsPerDayPerClient,
        Integer maxBookingsPerWeekForNewClient,
        LocalDateTime updatedAt
) {}
```

- [ ] **Step 3: Create the mapper**

```java
package com.luxpretty.app.bookings.web.mapper;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.web.dto.BookingPolicyResponse;

public final class BookingPolicyMapper {

    private BookingPolicyMapper() {}

    public static BookingPolicyResponse toResponse(BookingPolicy entity) {
        return new BookingPolicyResponse(
                entity.getMaxBookingsPerDayPerClient(),
                entity.getMaxBookingsPerWeekForNewClient(),
                entity.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 4: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/web/dto/UpdateBookingPolicyRequest.java \
        backend/src/main/java/com/luxpretty/app/bookings/web/dto/BookingPolicyResponse.java \
        backend/src/main/java/com/luxpretty/app/bookings/web/mapper/BookingPolicyMapper.java
git commit -m "feat(booking-policy): add request/response DTOs and mapper"
```

---

## Task 8: Controller `BookingPolicyController` (TDD)

**Files:**
- Create: `backend/src/main/java/com/luxpretty/app/bookings/web/BookingPolicyController.java`
- Test: `backend/src/test/java/com/luxpretty/app/bookings/web/BookingPolicyControllerTests.java`

- [ ] **Step 1: Write the failing controller test**

Look at an existing `@WebMvcTest` (e.g. `backend/src/test/java/com/luxpretty/app/availability/web/AvailabilityControllerValidationTests.java`) for the project's test boilerplate (mocked OIDC user service, security disabled-or-not patterns). Mirror it here.

```java
package com.luxpretty.app.bookings.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.bookings.app.BookingPolicyService;
import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.web.dto.UpdateBookingPolicyRequest;
import com.luxpretty.app.core.auth.CustomOidcUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingPolicyController.class)
class BookingPolicyControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean BookingPolicyService service;
    @MockBean CustomOidcUserService oidcUserService;

    private BookingPolicy fixture(int perDay, int perWeek) {
        BookingPolicy p = new BookingPolicy();
        p.setId(1L);
        p.setMaxBookingsPerDayPerClient(perDay);
        p.setMaxBookingsPerWeekForNewClient(perWeek);
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    @Test
    void getRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/pro/booking-policy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void getForbiddenForNonPro() throws Exception {
        mvc.perform(get("/api/pro/booking-policy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PRO")
    void getReturnsCurrentPolicy() throws Exception {
        when(service.getOrCreatePolicy()).thenReturn(fixture(1, 1));

        mvc.perform(get("/api/pro/booking-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBookingsPerDayPerClient").value(1))
                .andExpect(jsonPath("$.maxBookingsPerWeekForNewClient").value(1));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void putAcceptsValidUpdate() throws Exception {
        when(service.update(anyInt(), anyInt())).thenReturn(fixture(3, 2));

        UpdateBookingPolicyRequest body = new UpdateBookingPolicyRequest(3, 2);
        mvc.perform(put("/api/pro/booking-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBookingsPerDayPerClient").value(3))
                .andExpect(jsonPath("$.maxBookingsPerWeekForNewClient").value(2));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void putRejectsZero() throws Exception {
        UpdateBookingPolicyRequest body = new UpdateBookingPolicyRequest(0, 1);
        mvc.perform(put("/api/pro/booking-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PRO")
    void putRejectsAboveMax() throws Exception {
        UpdateBookingPolicyRequest body = new UpdateBookingPolicyRequest(1, 99);
        mvc.perform(put("/api/pro/booking-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run tests, expect failure**

Run: `cd backend && mvn -q -Dtest=BookingPolicyControllerTests test`
Expected: COMPILATION ERROR (controller class missing).

- [ ] **Step 3: Implement the controller**

```java
package com.luxpretty.app.bookings.web;

import com.luxpretty.app.bookings.app.BookingPolicyService;
import com.luxpretty.app.bookings.web.dto.BookingPolicyResponse;
import com.luxpretty.app.bookings.web.dto.UpdateBookingPolicyRequest;
import com.luxpretty.app.bookings.web.mapper.BookingPolicyMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pro/booking-policy")
@PreAuthorize("hasRole('PRO')")
public class BookingPolicyController {

    private final BookingPolicyService service;

    public BookingPolicyController(BookingPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public BookingPolicyResponse get() {
        return BookingPolicyMapper.toResponse(service.getOrCreatePolicy());
    }

    @PutMapping
    public BookingPolicyResponse update(@Valid @RequestBody UpdateBookingPolicyRequest req) {
        return BookingPolicyMapper.toResponse(
                service.update(req.maxBookingsPerDayPerClient(), req.maxBookingsPerWeekForNewClient())
        );
    }
}
```

- [ ] **Step 4: Run tests, expect green**

Run: `cd backend && mvn -q -Dtest=BookingPolicyControllerTests test`
Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/web/BookingPolicyController.java \
        backend/src/test/java/com/luxpretty/app/bookings/web/BookingPolicyControllerTests.java
git commit -m "feat(booking-policy): add /api/pro/booking-policy GET/PUT endpoints"
```

---

## Task 9: Wire `validateClientLimits` into `CareBookingService.createClientBooking`

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java`

- [ ] **Step 1: Inject the new service**

In the field declarations + constructor of `CareBookingService`, add `BookingPolicyService bookingPolicyService` (mirror the existing `@RequiredArgsConstructor` pattern if Lombok is used, otherwise add it to the explicit constructor — check the file to confirm which pattern is in place).

Add the import:
```java
import com.luxpretty.app.bookings.app.BookingPolicyService;
```

(adjust if `BookingPolicyService` lives in the same package — in that case no import is needed.)

- [ ] **Step 2: Call the validator from `createClientBooking`**

Inside `createClientBooking(...)`, immediately after the existing past-date guard at line 308 (`if (req.appointmentDate().isBefore(LocalDate.now()))`) and BEFORE the min/max-advance checks, insert:

```java
        // Per-tenant booking-policy guard (max bookings/day per client + max
        // bookings/week for first-time clients). Throws BookingLimitExceededException
        // → mapped to HTTP 409 with a typed error code by GlobalExceptionHandler.
        bookingPolicyService.validateClientLimits(client.getId(), req.appointmentDate());
```

- [ ] **Step 3: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Run the existing booking tests to ensure no regression**

Run: `cd backend && mvn -q -Dtest='CareBooking*,Booking*' test`
Expected: all green. (If a pre-existing test breaks because it now triggers the limit, fix it by stubbing `bookingPolicyService.validateClientLimits` to do nothing in that test's setup.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/bookings/app/CareBookingService.java
git commit -m "feat(booking-policy): enforce client booking limits in createClientBooking"
```

---

## Task 10: Integration test `BookingServiceLimitsIntegrationTests`

**Files:**
- Test: `backend/src/test/java/com/luxpretty/app/bookings/app/BookingServiceLimitsIntegrationTests.java`

- [ ] **Step 1: Write the integration test**

Use the same `@DataJpaTest` + manual service wiring pattern that other integration tests use in this codebase (see `backend/src/test/java/com/luxpretty/app/availability/app/SlotAvailabilityServiceTests.java` for the pattern).

```java
package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.BookingPolicyRepository;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.common.error.BookingLimitExceededException;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(BookingPolicyService.class)
class BookingServiceLimitsIntegrationTests {

    @Autowired UserRepository userRepo;
    @Autowired CareRepository careRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired CareBookingRepository bookingRepo;
    @Autowired BookingPolicyRepository policyRepo;
    @Autowired BookingPolicyService policyService;

    private User client;
    private Care care;

    @BeforeEach
    void setUp() {
        client = new User();
        client.setEmail("alice@test.com");
        client.setName("Alice");
        client.setRole(Role.CLIENT);
        client = userRepo.save(client);

        Category cat = new Category();
        cat.setName("Visage");
        cat = categoryRepo.save(cat);

        care = new Care();
        care.setName("Soin éclat");
        care.setPrice(80);
        care.setDescription("desc");
        care.setStatus(CareStatus.ACTIVE);
        care.setDuration(60);
        care.setCategory(cat);
        care = careRepo.save(care);

        BookingPolicy policy = new BookingPolicy();
        policy.setMaxBookingsPerDayPerClient(1);
        policy.setMaxBookingsPerWeekForNewClient(1);
        policy.setUpdatedAt(LocalDateTime.now());
        policyRepo.save(policy);
    }

    private CareBooking persistBooking(LocalDate date, LocalTime time, CareBookingStatus status) {
        CareBooking b = new CareBooking();
        b.setUser(client);
        b.setCare(care);
        b.setQuantity(1);
        b.setAppointmentDate(date);
        b.setAppointmentTime(time);
        b.setStatus(status);
        b.setCreatedAt(LocalDateTime.now());
        return bookingRepo.save(b);
    }

    @Test
    void rejectsSecondBookingSameDay() {
        LocalDate day = LocalDate.of(2026, 5, 15);
        persistBooking(day, LocalTime.of(10, 0), CareBookingStatus.PENDING);

        assertThatThrownBy(() -> policyService.validateClientLimits(client.getId(), day))
                .isInstanceOfSatisfying(BookingLimitExceededException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(BookingLimitExceededException.CODE_DAILY));
    }

    @Test
    void rejectsNewClientSecondBookingSameWeek() {
        // Tuesday 2026-05-12 → already booked
        persistBooking(LocalDate.of(2026, 5, 12), LocalTime.of(10, 0), CareBookingStatus.PENDING);

        // Try to book Thursday 2026-05-14 (same ISO week) — must fail with weekly code
        assertThatThrownBy(() -> policyService.validateClientLimits(client.getId(), LocalDate.of(2026, 5, 14)))
                .isInstanceOfSatisfying(BookingLimitExceededException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(BookingLimitExceededException.CODE_NEW_CLIENT_WEEKLY));
    }

    @Test
    void existingClientNotConstrainedByWeeklyLimit() {
        // Past confirmed booking → user is no longer "new"
        persistBooking(LocalDate.now().minusDays(30), LocalTime.of(10, 0), CareBookingStatus.CONFIRMED);

        // Two new bookings on different days of the same future week — both should pass the weekly check
        // (the daily check still allows 1/day, so we only validate one day at a time)
        assertThatCode(() -> policyService.validateClientLimits(client.getId(), LocalDate.of(2026, 5, 12)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policyService.validateClientLimits(client.getId(), LocalDate.of(2026, 5, 14)))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd backend && mvn -q -Dtest=BookingServiceLimitsIntegrationTests test`
Expected: `Tests run: 3, Failures: 0`. If `@DataJpaTest` complains about missing schema, mirror what `SlotAvailabilityServiceTests` does (e.g. an `@AutoConfigureTestDatabase` annotation).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/luxpretty/app/bookings/app/BookingServiceLimitsIntegrationTests.java
git commit -m "test(booking-policy): integration tests for limit enforcement"
```

---

## Task 11: Flyway migration for the per-tenant table

**Files:**
- Create: `backend/src/main/resources/db/migration/tenant/V2__create_booking_policy.sql`

- [ ] **Step 1: Create the migration**

```sql
-- Per-tenant booking policy. Singleton row inserted with safe defaults.
-- See docs/superpowers/specs/2026-05-11-booking-policy-guards-design.md.

CREATE TABLE "${tenantSchema}".BOOKING_POLICY (
    ID                                       NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    MAX_BOOKINGS_PER_DAY_PER_CLIENT          NUMBER(2) NOT NULL,
    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT     NUMBER(2) NOT NULL,
    UPDATED_AT                               TIMESTAMP NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON "${tenantSchema}".BOOKING_POLICY TO "${appSchema}";

INSERT INTO "${tenantSchema}".BOOKING_POLICY (
    MAX_BOOKINGS_PER_DAY_PER_CLIENT,
    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT,
    UPDATED_AT
) VALUES (1, 1, CURRENT_TIMESTAMP);
```

- [ ] **Step 2: Verify the migration filename does not collide**

Run: `ls backend/src/main/resources/db/migration/tenant/`
Expected: `V1__baseline.sql V2__create_booking_policy.sql`. If a V2 already exists, rename to the next free V number and update the filename in the commit.

- [ ] **Step 3: Mirror schema creation into `TenantSchemaManager.createTenantTables()`**

Open `backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaManager.java` (or whichever class the V1 baseline header references — `grep -n "createTenantTables" backend/src/main/java -r`) and add a CREATE TABLE statement matching the SQL above. The header of `V1__baseline.sql` says: *"Mirrored from TenantSchemaManager.createTenantTables(); keep both in sync."* Skip this step only if your grep shows the manager has already been deprecated — otherwise perform it.

- [ ] **Step 4: Run the full backend test suite**

Run: `cd backend && mvn -q test`
Expected: all green (553+3 = 556 tests). If any test fails because a tenant schema is missing the new table, that's a sign Step 3 was skipped or incomplete — fix and re-run.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/tenant/V2__create_booking_policy.sql \
        backend/src/main/java/com/luxpretty/app/multitenancy/TenantSchemaManager.java
git commit -m "feat(booking-policy): Flyway migration for BOOKING_POLICY per tenant"
```

---

## Task 12: Frontend model + service (TDD)

**Files:**
- Create: `frontend/src/app/features/availability/booking-policy/booking-policy.model.ts`
- Create: `frontend/src/app/features/availability/booking-policy/booking-policy.service.ts`
- Test: `frontend/src/app/features/availability/booking-policy/booking-policy.service.spec.ts`

- [ ] **Step 1: Create the model**

```typescript
export interface BookingPolicy {
  readonly maxBookingsPerDayPerClient: number;
  readonly maxBookingsPerWeekForNewClient: number;
  readonly updatedAt: string;
}

export interface UpdateBookingPolicyRequest {
  readonly maxBookingsPerDayPerClient: number;
  readonly maxBookingsPerWeekForNewClient: number;
}
```

- [ ] **Step 2: Write the failing service test**

```typescript
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/config/api.config';
import { BookingPolicyService } from './booking-policy.service';

describe('BookingPolicyService', () => {
  let service: BookingPolicyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://test' },
        BookingPolicyService,
      ],
    });
    service = TestBed.inject(BookingPolicyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs the current policy', () => {
    let received: any;
    service.getCurrent().subscribe((p) => (received = p));
    const req = http.expectOne('http://test/api/pro/booking-policy');
    expect(req.request.method).toBe('GET');
    req.flush({ maxBookingsPerDayPerClient: 1, maxBookingsPerWeekForNewClient: 1, updatedAt: '2026-05-11T10:00:00' });
    expect(received.maxBookingsPerDayPerClient).toBe(1);
  });

  it('PUTs an update with the request body', () => {
    service.update({ maxBookingsPerDayPerClient: 3, maxBookingsPerWeekForNewClient: 2 }).subscribe();
    const req = http.expectOne('http://test/api/pro/booking-policy');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ maxBookingsPerDayPerClient: 3, maxBookingsPerWeekForNewClient: 2 });
    req.flush({ maxBookingsPerDayPerClient: 3, maxBookingsPerWeekForNewClient: 2, updatedAt: '2026-05-11T10:00:00' });
  });

  it('propagates HTTP errors to the observable', (done) => {
    service.getCurrent().subscribe({
      next: () => done.fail('should have errored'),
      error: (err) => {
        expect(err.status).toBe(500);
        done();
      },
    });
    http.expectOne('http://test/api/pro/booking-policy').flush('boom', { status: 500, statusText: 'err' });
  });
});
```

- [ ] **Step 3: Run, expect failure**

Run: `cd frontend && npm test -- --include='**/booking-policy.service.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: COMPILATION ERROR (`BookingPolicyService` does not exist).

- [ ] **Step 4: Implement the service**

```typescript
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { BookingPolicy, UpdateBookingPolicyRequest } from './booking-policy.model';

@Injectable({ providedIn: 'root' })
export class BookingPolicyService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  getCurrent(): Observable<BookingPolicy> {
    return this.http.get<BookingPolicy>(`${this.base}/api/pro/booking-policy`);
  }

  update(req: UpdateBookingPolicyRequest): Observable<BookingPolicy> {
    return this.http.put<BookingPolicy>(`${this.base}/api/pro/booking-policy`, req);
  }
}
```

(If the project's `API_BASE_URL` import path differs, fix it — `grep -rn "API_BASE_URL" frontend/src/app/core` to confirm.)

- [ ] **Step 5: Run, expect green**

Run: `cd frontend && npm test -- --include='**/booking-policy.service.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: 3 SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/availability/booking-policy/
git commit -m "feat(booking-policy): add frontend service + model with HTTP tests"
```

---

## Task 13: Frontend store (TDD)

**Files:**
- Create: `frontend/src/app/features/availability/booking-policy/booking-policy.store.ts`
- Test: `frontend/src/app/features/availability/booking-policy/booking-policy.store.spec.ts`

- [ ] **Step 1: Inspect an existing store to mirror conventions**

Run: `cat frontend/src/app/features/availability/closed-days.store.ts | head -60`
Read the patterns: `signalStore`, `withState`, `withRequestStatus`, `withMethods` with `rxMethod`. Mirror them exactly — same import paths, same `withRequestStatus` helper, same `setPending/setFulfilled/setError` pattern.

- [ ] **Step 2: Write the failing store test**

```typescript
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { BookingPolicyService } from './booking-policy.service';
import { BookingPolicyStore } from './booking-policy.store';

describe('BookingPolicyStore', () => {
  let store: InstanceType<typeof BookingPolicyStore>;
  let service: jasmine.SpyObj<BookingPolicyService>;

  beforeEach(() => {
    service = jasmine.createSpyObj<BookingPolicyService>('BookingPolicyService', ['getCurrent', 'update']);
    TestBed.configureTestingModule({
      providers: [{ provide: BookingPolicyService, useValue: service }, BookingPolicyStore],
    });
    store = TestBed.inject(BookingPolicyStore);
  });

  it('load() puts policy into state and marks fulfilled', () => {
    service.getCurrent.and.returnValue(of({
      maxBookingsPerDayPerClient: 2,
      maxBookingsPerWeekForNewClient: 1,
      updatedAt: '2026-05-11T10:00:00',
    }));
    store.load();
    expect(store.policy()).toEqual(jasmine.objectContaining({ maxBookingsPerDayPerClient: 2 }));
    expect(store.isFullfilled()).toBeTrue();
  });

  it('update() patches state with the response', () => {
    service.update.and.returnValue(of({
      maxBookingsPerDayPerClient: 4,
      maxBookingsPerWeekForNewClient: 3,
      updatedAt: '2026-05-11T10:00:00',
    }));
    store.update({ maxBookingsPerDayPerClient: 4, maxBookingsPerWeekForNewClient: 3 });
    expect(store.policy()?.maxBookingsPerDayPerClient).toBe(4);
    expect(store.policy()?.maxBookingsPerWeekForNewClient).toBe(3);
  });

  it('load() error sets error and leaves policy null', () => {
    service.getCurrent.and.returnValue(throwError(() => new Error('boom')));
    store.load();
    expect(store.policy()).toBeNull();
    expect(store.error()).toBeTruthy();
  });

  it('update() error preserves previous policy', () => {
    service.getCurrent.and.returnValue(of({
      maxBookingsPerDayPerClient: 1, maxBookingsPerWeekForNewClient: 1, updatedAt: 'x',
    }));
    store.load();
    service.update.and.returnValue(throwError(() => new Error('nope')));
    store.update({ maxBookingsPerDayPerClient: 5, maxBookingsPerWeekForNewClient: 5 });
    expect(store.policy()?.maxBookingsPerDayPerClient).toBe(1);
    expect(store.error()).toBeTruthy();
  });
});
```

- [ ] **Step 3: Run, expect failure**

Run: `cd frontend && npm test -- --include='**/booking-policy.store.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: COMPILATION ERROR (`BookingPolicyStore` not found).

- [ ] **Step 4: Implement the store**

(Adjust the `withRequestStatus` import path to match the project's existing one — confirm via Step 1's `cat`.)

```typescript
import { computed, inject } from '@angular/core';
import { signalStore, withComputed, withMethods, withState, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request-status';
import { BookingPolicy, UpdateBookingPolicyRequest } from './booking-policy.model';
import { BookingPolicyService } from './booking-policy.service';

interface State {
  policy: BookingPolicy | null;
}

export const BookingPolicyStore = signalStore(
  withState<State>({ policy: null }),
  withRequestStatus(),
  withMethods((store, service = inject(BookingPolicyService)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.getCurrent().pipe(
            tap({
              next: (policy) => patchState(store, { policy }, setFulfilled()),
              error: (err) => patchState(store, setError(String(err))),
            }),
          ),
        ),
      ),
    ),
    update: rxMethod<UpdateBookingPolicyRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap((req) =>
          service.update(req).pipe(
            tap({
              next: (policy) => patchState(store, { policy }, setFulfilled()),
              error: (err) => patchState(store, setError(String(err))),
            }),
          ),
        ),
      ),
    ),
  })),
);
```

- [ ] **Step 5: Run, expect green**

Run: `cd frontend && npm test -- --include='**/booking-policy.store.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: 4 SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/availability/booking-policy/booking-policy.store.ts \
        frontend/src/app/features/availability/booking-policy/booking-policy.store.spec.ts
git commit -m "feat(booking-policy): add SignalStore for booking policy"
```

---

## Task 14: Frontend component `BookingPolicyComponent` (TDD)

**Files:**
- Create: `frontend/src/app/features/availability/booking-policy/booking-policy.component.ts`
- Create: `frontend/src/app/features/availability/booking-policy/booking-policy.component.html`
- Create: `frontend/src/app/features/availability/booking-policy/booking-policy.component.scss`
- Test: `frontend/src/app/features/availability/booking-policy/booking-policy.component.spec.ts`
- Modify: `frontend/public/i18n/fr.json` and `frontend/public/i18n/en.json`

- [ ] **Step 1: Add the i18n keys to BOTH `fr.json` and `en.json`**

In `frontend/public/i18n/fr.json`, locate the `"pro"` block and add inside it:

```json
    "bookingPolicy": {
      "tab": "Règles de réservation",
      "title": "Limites de réservation client",
      "subtitle": "Empêche un même client de monopoliser vos créneaux.",
      "maxPerDay": {
        "label": "Rendez-vous max par jour pour un même client",
        "help": "Empêche un client de prendre plusieurs rendez-vous le même jour."
      },
      "maxPerWeekNew": {
        "label": "Rendez-vous max par semaine pour un nouveau client",
        "help": "Limite les nouveaux clients (jamais venus) à ce nombre de rdv par semaine."
      },
      "save": "Enregistrer",
      "saved": "Règles mises à jour",
      "error": "Erreur lors de la sauvegarde"
    },
```

In `frontend/public/i18n/en.json`, the same nested block at the same location:

```json
    "bookingPolicy": {
      "tab": "Booking rules",
      "title": "Client booking limits",
      "subtitle": "Prevents a single client from monopolizing your slots.",
      "maxPerDay": {
        "label": "Max bookings per day per client",
        "help": "Prevents a client from taking multiple appointments on the same day."
      },
      "maxPerWeekNew": {
        "label": "Max bookings per week for a new client",
        "help": "Limits brand-new clients (never seen before) to this many bookings per week."
      },
      "save": "Save",
      "saved": "Rules updated",
      "error": "Save failed"
    },
```

Also add at the top-level `errors.booking` block (create the path if it does not exist) in fr.json:
```json
    "limitDaily": "Vous avez déjà un rendez-vous ce jour-là chez ce salon.",
    "limitNewClientWeekly": "Pour une première visite, un seul rendez-vous est autorisé cette semaine."
```

And in en.json:
```json
    "limitDaily": "You already have a booking that day at this salon.",
    "limitNewClientWeekly": "First visits are limited to one booking per week."
```

Validate the JSON: `cd frontend && node -e "JSON.parse(require('fs').readFileSync('public/i18n/fr.json'))"` and same for en.json. Expected: no output (= valid).

- [ ] **Step 2: Write the failing component test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { signal } from '@angular/core';
import { BookingPolicyComponent } from './booking-policy.component';
import { BookingPolicyStore } from './booking-policy.store';

describe('BookingPolicyComponent', () => {
  let fixture: ComponentFixture<BookingPolicyComponent>;
  let storeSpy: any;

  beforeEach(async () => {
    storeSpy = {
      policy: signal({
        maxBookingsPerDayPerClient: 1,
        maxBookingsPerWeekForNewClient: 1,
        updatedAt: '2026-05-11T10:00:00',
      }),
      isPending: signal(false),
      isFullfilled: signal(true),
      error: signal(null),
      load: jasmine.createSpy('load'),
      update: jasmine.createSpy('update'),
    };

    await TestBed.configureTestingModule({
      imports: [
        BookingPolicyComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {}, fr: {} },
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr', 'en'] },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: BookingPolicyStore, useValue: storeSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingPolicyComponent);
    fixture.detectChanges();
  });

  it('initializes form from store policy', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
    expect((inputs[0] as HTMLInputElement).value).toBe('1');
    expect((inputs[1] as HTMLInputElement).value).toBe('1');
  });

  it('save button calls store.update with the form values', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]') as NodeListOf<HTMLInputElement>;
    inputs[0].value = '3';
    inputs[0].dispatchEvent(new Event('input'));
    inputs[1].value = '2';
    inputs[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button.save-btn');
    button.click();

    expect(storeSpy.update).toHaveBeenCalledWith({
      maxBookingsPerDayPerClient: 3,
      maxBookingsPerWeekForNewClient: 2,
    });
  });

  it('disables save button when value is below 1', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]') as NodeListOf<HTMLInputElement>;
    inputs[0].value = '0';
    inputs[0].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button.save-btn');
    expect(button.disabled).toBeTrue();
  });

  it('disables save button while pending', () => {
    storeSpy.isPending.set(true);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button.save-btn');
    expect(button.disabled).toBeTrue();
  });
});
```

- [ ] **Step 3: Run, expect failure**

Run: `cd frontend && npm test -- --include='**/booking-policy.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: COMPILATION ERROR (component missing).

- [ ] **Step 4: Implement the component class**

```typescript
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BookingPolicyStore } from './booking-policy.store';

@Component({
  selector: 'app-booking-policy',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  providers: [BookingPolicyStore],
  templateUrl: './booking-policy.component.html',
  styleUrl: './booking-policy.component.scss',
})
export class BookingPolicyComponent {
  readonly store = inject(BookingPolicyStore);
  private readonly snackbar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected readonly perDay = signal(1);
  protected readonly perWeekNew = signal(1);

  protected readonly canSave = computed(
    () =>
      !this.store.isPending() &&
      this.perDay() >= 1 &&
      this.perDay() <= 10 &&
      this.perWeekNew() >= 1 &&
      this.perWeekNew() <= 10,
  );

  private lastFulfilledShown = false;
  private lastErrorShown: string | null = null;

  constructor() {
    this.store.load();
    effect(() => {
      const policy = this.store.policy();
      if (policy) {
        this.perDay.set(policy.maxBookingsPerDayPerClient);
        this.perWeekNew.set(policy.maxBookingsPerWeekForNewClient);
      }
    });
    effect(() => {
      if (this.store.isFullfilled() && this.lastFulfilledShown === false && this.store.policy()) {
        // Only show "saved" snackbar after an explicit save (not after the initial load)
      }
    });
    effect(() => {
      const err = this.store.error();
      if (err && err !== this.lastErrorShown) {
        this.lastErrorShown = err;
        this.snackbar.open(this.transloco.translate('pro.bookingPolicy.error'), undefined, { duration: 4000 });
      }
    });
  }

  protected onSave(): void {
    this.store.update({
      maxBookingsPerDayPerClient: this.perDay(),
      maxBookingsPerWeekForNewClient: this.perWeekNew(),
    });
    // Snackbar success: triggered when store transitions to fulfilled after the call.
    // Watch the store and show once when the update settles successfully:
    const unsub = effect(
      (onCleanup) => {
        if (this.store.isFullfilled() && !this.store.isPending()) {
          this.snackbar.open(this.transloco.translate('pro.bookingPolicy.saved'), undefined, { duration: 3000 });
          onCleanup(() => unsub.destroy());
        }
      },
      { manualCleanup: true },
    );
  }
}
```

- [ ] **Step 5: Implement the template**

```html
<section class="booking-policy">
  <header>
    <h2>{{ 'pro.bookingPolicy.title' | transloco }}</h2>
    <p class="subtitle">{{ 'pro.bookingPolicy.subtitle' | transloco }}</p>
  </header>

  <div class="field">
    <label for="bp-day">{{ 'pro.bookingPolicy.maxPerDay.label' | transloco }}</label>
    <input
      id="bp-day"
      type="number"
      min="1"
      max="10"
      [ngModel]="perDay()"
      (ngModelChange)="perDay.set(+$event)"
    />
    <small class="help">{{ 'pro.bookingPolicy.maxPerDay.help' | transloco }}</small>
  </div>

  <div class="field">
    <label for="bp-week">{{ 'pro.bookingPolicy.maxPerWeekNew.label' | transloco }}</label>
    <input
      id="bp-week"
      type="number"
      min="1"
      max="10"
      [ngModel]="perWeekNew()"
      (ngModelChange)="perWeekNew.set(+$event)"
    />
    <small class="help">{{ 'pro.bookingPolicy.maxPerWeekNew.help' | transloco }}</small>
  </div>

  <div class="actions">
    <button
      type="button"
      class="save-btn"
      mat-flat-button
      color="primary"
      [disabled]="!canSave()"
      (click)="onSave()"
    >
      @if (store.isPending()) {
        <mat-spinner diameter="18"></mat-spinner>
      } @else {
        {{ 'pro.bookingPolicy.save' | transloco }}
      }
    </button>
  </div>
</section>
```

- [ ] **Step 6: Implement the styles**

```scss
:host {
  display: block;
}

.booking-policy {
  max-width: 560px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 18px;

  header h2 {
    font-size: 18px;
    font-weight: 600;
    margin: 0 0 4px;
  }

  .subtitle {
    color: #6b5560;
    font-size: 13px;
    margin: 0;
  }
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 13px;
    font-weight: 600;
    color: #2c1c20;
  }

  input[type='number'] {
    width: 96px;
    padding: 8px 10px;
    border: 1px solid #ead7df;
    border-radius: 8px;
    font-size: 14px;
  }

  .help {
    color: #8a727a;
    font-size: 12px;
  }
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.save-btn {
  min-width: 120px;
}
```

- [ ] **Step 7: Run, expect green**

Run: `cd frontend && npm test -- --include='**/booking-policy.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: 4 SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/features/availability/booking-policy/ frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(booking-policy): add BookingPolicyComponent with FR/EN i18n"
```

---

## Task 15: Integrate as a tab in `/pro/availability`

**Files:**
- Modify: `frontend/src/app/features/availability/availability.component.html`
- Modify: `frontend/src/app/features/availability/availability.component.ts`
- Modify: `frontend/src/app/features/availability/availability.component.spec.ts`

- [ ] **Step 1: Update the availability component to import the tab module + new component**

In `availability.component.ts`:
- Add to `imports` array: `MatTabsModule` and `BookingPolicyComponent`.
- Add the imports at the top:
  ```typescript
  import { MatTabsModule } from '@angular/material/tabs';
  import { BookingPolicyComponent } from './booking-policy/booking-policy.component';
  ```

- [ ] **Step 2: Wrap the existing template content in a tab group**

Open `availability.component.html` and wrap the entire current content of `<div class="availability-page">` (or whichever wrapper exists — read the file first) in a `mat-tab-group`. Schematic structure:

```html
<mat-tab-group>
  <mat-tab [label]="'pro.availability.title' | transloco">
    <!-- existing content stays here unchanged -->
  </mat-tab>

  <mat-tab [label]="'pro.bookingPolicy.tab' | transloco">
    <app-booking-policy></app-booking-policy>
  </mat-tab>
</mat-tab-group>
```

If the existing template uses a top-level header outside the tabs (e.g. page title), keep it outside the `mat-tab-group`.

- [ ] **Step 3: Update the existing component spec**

Add to `availability.component.spec.ts` (alongside the existing tests):

```typescript
it('renders two tabs: hours and booking rules', () => {
  fixture.detectChanges();
  const tabs = fixture.nativeElement.querySelectorAll('mat-tab, [role="tab"]');
  expect(tabs.length).toBeGreaterThanOrEqual(2);
});
```

You may need to add `provideNoopAnimations()` to the `TestBed` providers if it isn't there already (Material tabs need it).

- [ ] **Step 4: Run the availability tests**

Run: `cd frontend && npm test -- --include='**/availability.component.spec.ts' --watch=false --browsers=ChromeHeadless`
Expected: all green, including the new test.

- [ ] **Step 5: Manually verify in the browser**

Open `http://localhost:4300/pro/availability` and confirm:
- 2 tabs are visible.
- Tab 1 shows the existing timeline.
- Tab 2 shows the booking-policy form with current values.

If the dev server isn't running: `docker compose --profile dev up -d frontend-dev` (CLAUDE.md context).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/availability/availability.component.html \
        frontend/src/app/features/availability/availability.component.ts \
        frontend/src/app/features/availability/availability.component.spec.ts
git commit -m "feat(booking-policy): expose Booking rules as a tab in /pro/availability"
```

---

## Task 16: Surface 409 error codes in the client booking flow

**Files (to be confirmed first):**
- Modify: the bookings service that performs the POST booking request.
- Modify: the bookings component that calls it (modal or stepper).
- Test: the same component spec.

- [ ] **Step 1: Locate the POST booking call site**

Run:
```bash
grep -rn "/api/bookings\|/api/client-bookings\|createBooking\|createClientBooking" \
  frontend/src/app/features/bookings/services frontend/src/app/features/bookings/store frontend/src/app/features/bookings/components frontend/src/app/features/bookings/modals
```

Identify (a) the service method that issues the POST and (b) the component that subscribes to it. Note the exact file paths in your task notes — they replace the `<bookings.service>` / `<modal-or-stepper>` placeholders.

- [ ] **Step 2: Locate the existing error-handling branch in the component**

In the component identified above, find where it currently handles HTTP errors from the booking POST (typically a `subscribe({ error: (err) => ... })` block or a `catchError` in the store). That's where the new 409 branches go.

- [ ] **Step 3: Write the failing tests**

Add these tests to the component's spec file:

```typescript
import { HttpErrorResponse } from '@angular/common/http';

it('shows daily-limit message on 409 BOOKING_LIMIT_DAILY_EXCEEDED', () => {
  const snackbar = TestBed.inject(MatSnackBar);
  spyOn(snackbar, 'open');
  // Stub the booking service to error with the typed 409 body
  (bookingsServiceSpy.create as jasmine.Spy).and.returnValue(
    throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'BOOKING_LIMIT_DAILY_EXCEEDED', message: 'x', limit: 1, currentCount: 1 },
    })),
  );
  component.submit(); // or whatever method triggers the POST
  expect(snackbar.open).toHaveBeenCalledWith(
    jasmine.stringMatching(/déjà un rendez-vous|already have a booking/),
    jasmine.anything(),
    jasmine.anything(),
  );
});

it('shows new-client weekly message on 409 BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED', () => {
  const snackbar = TestBed.inject(MatSnackBar);
  spyOn(snackbar, 'open');
  (bookingsServiceSpy.create as jasmine.Spy).and.returnValue(
    throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED', message: 'x', limit: 1, currentCount: 1 },
    })),
  );
  component.submit();
  expect(snackbar.open).toHaveBeenCalledWith(
    jasmine.stringMatching(/première visite|first visits/i),
    jasmine.anything(),
    jasmine.anything(),
  );
});

it('falls back to generic message on 409 with unknown code', () => {
  const snackbar = TestBed.inject(MatSnackBar);
  spyOn(snackbar, 'open');
  (bookingsServiceSpy.create as jasmine.Spy).and.returnValue(
    throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'SOMETHING_ELSE', message: 'x' },
    })),
  );
  component.submit();
  expect(snackbar.open).toHaveBeenCalled();
});

it('still handles generic 500 error via existing branch', () => {
  const snackbar = TestBed.inject(MatSnackBar);
  spyOn(snackbar, 'open');
  (bookingsServiceSpy.create as jasmine.Spy).and.returnValue(
    throwError(() => new HttpErrorResponse({ status: 500, error: 'boom' })),
  );
  component.submit();
  expect(snackbar.open).toHaveBeenCalled();
});

it('keeps the selected slot after a 409 (no reset)', () => {
  const initialSlot = component.selectedSlot();
  (bookingsServiceSpy.create as jasmine.Spy).and.returnValue(
    throwError(() => new HttpErrorResponse({
      status: 409, error: { code: 'BOOKING_LIMIT_DAILY_EXCEEDED' },
    })),
  );
  component.submit();
  expect(component.selectedSlot()).toEqual(initialSlot);
});
```

(Adapt method names — `submit()`, `selectedSlot()`, `bookingsServiceSpy` — to what the actual component uses. Keep the spec mock in line with how the component currently mocks the service.)

- [ ] **Step 4: Run, expect failure**

Run: `cd frontend && npm test -- --include='<path-to-spec>' --watch=false --browsers=ChromeHeadless`
Expected: the new tests fail (no specialized handling yet).

- [ ] **Step 5: Implement the error branching in the component (or store)**

In the existing error branch of the booking POST, add this logic:

```typescript
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoService } from '@jsverse/transloco';

// inside the component constructor or wherever services are injected:
private readonly transloco = inject(TranslocoService);
private readonly snackbar = inject(MatSnackBar);

private handleBookingError(err: unknown): void {
  if (err instanceof HttpErrorResponse && err.status === 409) {
    const code = err.error?.code as string | undefined;
    if (code === 'BOOKING_LIMIT_DAILY_EXCEEDED') {
      this.snackbar.open(this.transloco.translate('errors.booking.limitDaily'), undefined, { duration: 5000 });
      return;
    }
    if (code === 'BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED') {
      this.snackbar.open(this.transloco.translate('errors.booking.limitNewClientWeekly'), undefined, { duration: 5000 });
      return;
    }
  }
  // Fallback to existing generic-error path:
  this.snackbar.open(this.transloco.translate('errors.api.serverError'), undefined, { duration: 4000 });
}
```

Hook `handleBookingError(err)` into the existing `subscribe({ error: ... })` block. Do NOT remove the existing fallback path — wrap it.

- [ ] **Step 6: Run, expect green**

Run: `cd frontend && npm test -- --include='<path-to-spec>' --watch=false --browsers=ChromeHeadless`
Expected: all tests green.

- [ ] **Step 7: Manually try the flow end-to-end**

In the dev environment (frontend on http://localhost:4300, backend running), as a CLIENT user:
- Book a slot → confirm.
- Try to book another slot the same day → expect a snackbar with the daily-limit message.
- Try (with a fresh user) to book 2 slots the same week → expect a snackbar with the new-client weekly message.

- [ ] **Step 8: Commit**

```bash
git add <paths-modified>
git commit -m "feat(booking-policy): surface 409 typed errors in client booking flow"
```

---

## Task 17: Final full-suite verification

- [ ] **Step 1: Run the entire backend test suite**

Run: `cd backend && mvn -B -ntp test`
Expected: all tests green. The new 17 tests (8 service + 6 controller + 3 integration) should be visible in the summary, total 553 + ~17 = ~570.

- [ ] **Step 2: Run the entire frontend test suite**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: all tests green, including the new 5 spec files (component, store, service, availability tabs, booking flow).

- [ ] **Step 3: Run a production build**

Run: `cd frontend && npm run build`
Expected: build succeeds without errors.

- [ ] **Step 4: Final commit if anything was tweaked during verification**

```bash
git status
# If clean, no commit needed. Otherwise:
git add . && git commit -m "chore(booking-policy): final fixups after full-suite run"
```

---

## Self-review notes

**Spec coverage:**
- Domain `BookingPolicy` → Task 1 ✓
- Repository (singleton finder) → Task 2 ✓
- 3 count queries on CareBookingRepository → Task 3 ✓
- BookingPolicyService (validate + getOrCreate + update) with 8 unit tests → Task 6 ✓
- BookingLimitExceededException + GlobalExceptionHandler 409 mapping → Tasks 4–5 ✓
- DTOs + mapper → Task 7 ✓
- Controller GET/PUT with @PreAuthorize PRO + validation 1–10 → Task 8 (6 tests) ✓
- Wiring into createClientBooking → Task 9 ✓
- Integration test (DataJpaTest) → Task 10 ✓
- Flyway per-tenant migration with default insert → Task 11 ✓
- Frontend service + tests → Task 12 ✓
- Frontend store + tests → Task 13 ✓
- Frontend component + tests + i18n FR/EN → Task 14 ✓
- Tabs integration in /pro/availability → Task 15 ✓
- Client booking flow 409 handling + 5 tests → Task 16 ✓

**Type/method consistency:** `validateClientLimits(Long, LocalDate)`, `getOrCreatePolicy()`, `update(int, int)` are used identically in service, controller, and test files. Frontend store methods `load()` / `update(req)` match between component and store spec. Error codes `BOOKING_LIMIT_DAILY_EXCEEDED` and `BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED` consistent across exception, controller test, integration test, service tests, and frontend i18n keys.

**No placeholders** in implementation steps. Two intentional ones (Task 14 `errors.api.serverError` fallback key, Task 16 `<path-to-spec>`) require the implementer to read existing code and substitute — these are clearly marked as "confirm first" tasks, not unspecified work.
