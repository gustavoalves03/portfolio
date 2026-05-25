package com.luxpretty.app.bookings.integration;

import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.web.dto.CareBookingRequest;
import com.luxpretty.app.bookings.web.dto.CareBookingResponse;
import com.luxpretty.app.common.error.BookingErrorCodes;
import com.luxpretty.app.employee.repo.EmployeeRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Oracle-specific smoke test for the function-based {@code UK_BOOKING_SLOT_EMPLOYEE}
 * unique index introduced in tenant migration V7 of the multi-employee booking plan
 * (see {@code 2026-05-21-chantier-b-multi-employee-booking.md}).
 *
 * <h2>Why Oracle and not H2?</h2>
 * <p>H2's Oracle-compatibility mode does not implement function-based indexes.
 * The unique constraint relies on the Oracle behaviour where {@code NULL} entries
 * are not stored in a unique index — this is what allows multiple {@code CANCELLED}
 * rows on the same {@code (date, time, employee_id)} slot to coexist without
 * violating uniqueness, while a {@code CONFIRMED} or {@code PENDING} row on that
 * same slot is correctly rejected. H2 simply cannot reproduce this.
 * (See also: {@code project_pending_testcontainers_oracle} and
 * {@code feedback_instant_oracle_timestamp_tz} project memories.)
 *
 * <h2>Test bodies are stubs pending fixture wiring</h2>
 * <p>The class structure validates that the test infrastructure compiles and is
 * opt-in. Each test body calls {@link org.junit.jupiter.api.Assertions#fail} with
 * a {@code TODO} message so that a CI run that accidentally enables the env var
 * does NOT silently pass — it will fail loudly with the stub message.
 *
 * <h2>How to opt in</h2>
 * <p>Requires Docker and ~3 GB free disk for the Oracle image.
 * <pre>
 *   TESTCONTAINERS_ORACLE=true mvn -pl backend test -Dtest=BookingConcurrencyOracleTests
 * </pre>
 *
 * <h2>Fixture wiring notes (for when this is fully implemented)</h2>
 * <ul>
 *   <li>Activate a tenant context via {@code TenantContext.setCurrentTenant(slug)} + a provisioned
 *       tenant schema before each test.</li>
 *   <li>Persist a {@code User}, a {@code Care} (status=ACTIVE), and an {@code Employee}
 *       with the care in {@code assignedCares}.</li>
 *   <li>Use {@link CareBookingService#create} for the concurrent-insert test, or directly
 *       insert via {@code CareBookingRepository} for the cancel-releases-slot test.</li>
 *   <li>The provisioning DataSource must also be pointed at the same Oracle container
 *       via a second {@code registry.add(...)} call in {@link #registerOracle}.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test-oracle")
@EnabledIfEnvironmentVariable(named = "TESTCONTAINERS_ORACLE", matches = "true")
class BookingConcurrencyOracleTests {

    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withReuse(true);

    @DynamicPropertySource
    static void registerOracle(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        // Provisioning DataSource must point at the same Oracle container so that
        // tenant-schema Flyway migrations (including V7 that creates UK_BOOKING_SLOT_EMPLOYEE)
        // run against real Oracle rather than H2.
        registry.add("app.multitenancy.provisioning.jdbc-url", oracle::getJdbcUrl);
        registry.add("app.multitenancy.provisioning.username", oracle::getUsername);
        registry.add("app.multitenancy.provisioning.password", oracle::getPassword);
    }

    @Autowired
    CareBookingService bookingService;

    @Autowired
    EmployeeRepository employeeRepository;

    /**
     * Two concurrent threads attempt to book the same (date, time, employee) slot.
     * The {@code UK_BOOKING_SLOT_EMPLOYEE} function-based index ensures exactly one
     * succeeds; the other receives a {@link ResponseStatusException} with HTTP 409
     * and reason {@link BookingErrorCodes#SLOT_TAKEN}.
     *
     * <p><b>TODO: wire fixture</b> — requires:
     * <ol>
     *   <li>Activate tenant context + provision tenant schema.</li>
     *   <li>Persist User, Care (ACTIVE), Employee (assigned to care).</li>
     *   <li>Build two identical {@link CareBookingRequest} records pointing at the
     *       same employeeId, date, time, and careId.</li>
     *   <li>Race both threads on {@code bookingService.create(req)}.</li>
     *   <li>Assert one completes normally and the other throws
     *       {@code ResponseStatusException(CONFLICT, SLOT_TAKEN)}.</li>
     * </ol>
     */
    @Test
    void concurrent_bookings_same_employee_same_slot_oneWinsOneGets409SlotTaken() {
        // TODO: wire fixture — see class javadoc for required setup steps.
        fail("Fixture not yet wired for concurrent_bookings_same_employee_same_slot_oneWinsOneGets409SlotTaken; "
                + "run with TESTCONTAINERS_ORACLE=true only after completing the fixture setup described in the class javadoc.");
    }

    /**
     * Verifies that cancelling a booking immediately releases the
     * {@code (date, time, employee_id)} slot so a subsequent booking at the same
     * coordinates succeeds.
     *
     * <p>This relies on Oracle's NULL-in-unique-index semantics: once a booking is
     * {@code CANCELLED} the function-based expression returns {@code NULL} for that
     * row, removing it from the unique index and freeing the slot for re-booking.
     * H2 in Oracle-compat mode does not reproduce this behaviour.
     *
     * <p><b>TODO: wire fixture</b> — requires:
     * <ol>
     *   <li>Activate tenant context + provision tenant schema.</li>
     *   <li>Persist User, Care (ACTIVE), Employee (assigned to care).</li>
     *   <li>Insert booking (employeeId, date, time) → assert status CONFIRMED/PENDING.</li>
     *   <li>Cancel via {@code bookingService.cancelBooking(id)}.</li>
     *   <li>Insert another booking with the same (employeeId, date, time) → must succeed.</li>
     * </ol>
     */
    @Test
    void cancelling_releases_slot_immediately() {
        // TODO: wire fixture — see class javadoc for required setup steps.
        fail("Fixture not yet wired for cancelling_releases_slot_immediately; "
                + "run with TESTCONTAINERS_ORACLE=true only after completing the fixture setup described in the class javadoc.");
    }
}
