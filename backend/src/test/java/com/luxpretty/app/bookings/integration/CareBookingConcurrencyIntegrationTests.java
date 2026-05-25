package com.luxpretty.app.bookings.integration;

import com.luxpretty.app.availability.domain.OpeningHour;
import com.luxpretty.app.availability.repo.OpeningHourRepository;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.bookings.web.dto.ClientBookingRequest;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.multitenancy.TenantSchemaManager;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test verifying that two concurrent client-booking
 * attempts for the exact same slot cannot both succeed. The test exercises the
 * real Spring context, Hibernate + H2, and the {@code UK_BOOKING_SLOT_EMPLOYEE}
 * unique constraint on {@code (appointment_date, appointment_time, employee_id)}.
 * Both threads target the same employee so the per-employee slot constraint
 * arbitrates the race — exactly one insert wins, the other receives 409.
 *
 * <p>Unit tests mock the repository, so they can't prove the DB-level race
 * protection actually works end-to-end. This test does.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CareBookingConcurrencyIntegrationTests {

    @Autowired
    private CareBookingService careBookingService;

    @Autowired
    private CareBookingRepository careBookingRepository;

    @Autowired
    private CareRepository careRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OpeningHourRepository openingHourRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantSchemaManager tenantSchemaManager;

    @Autowired
    private EmployeeRepository employeeRepository;

    private static final String TENANT_SLUG = "concurrency-salon";

    private Long careId;
    private Long clientUserId;
    private Long ownerUserId;
    private Long employeeId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;

    @BeforeEach
    void setUp() {
        // Create a tenant schema (idempotent even if the previous test class left one behind).
        // provisionSchema creates the base tables; migrateSchema adds later columns
        // (e.g. OPENING_HOURS.EMPLOYEE_ID, CARE_BOOKINGS.SALON_CLIENT_ID) that
        // the current entities expect.
        tenantSchemaManager.provisionSchema(TENANT_SLUG);
        tenantSchemaManager.migrateSchema(TENANT_SLUG);

        // Seed the tenant-independent "owner" user in the default (APPUSER) schema
        TenantContext.clear();
        User owner = userRepository.findByEmail("owner-concurrency@test.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Owner Concurrency")
                        .email("owner-concurrency@test.com")
                        .password("password")
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .build()));
        ownerUserId = owner.getId();

        User client = userRepository.findByEmail("client-concurrency@test.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Client Concurrency")
                        .email("client-concurrency@test.com")
                        .password("password")
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .build()));
        clientUserId = client.getId();

        // Tenant row (shared application schema)
        Tenant tenant = tenantRepository.findBySlug(TENANT_SLUG).orElseGet(() ->
                tenantRepository.save(Tenant.builder()
                        .slug(TENANT_SLUG)
                        .name("Concurrency Salon")
                        .ownerId(ownerUserId)
                        .build()));

        // All tenant-scoped seed data must be written inside the tenant schema
        TenantContext.setCurrentTenant(TENANT_SLUG);
        try {
            Category category = new Category();
            category.setName("Face Care");
            category.setDescription("Facial treatments");
            category = categoryRepository.save(category);

            Care care = new Care();
            care.setName("Cleansing facial");
            care.setPrice(5500);
            care.setDescription("30 min relaxing cleanse");
            care.setDuration(30);
            care.setStatus(CareStatus.ACTIVE);
            care.setCategory(category);
            care = careRepository.save(care);
            careId = care.getId();

            // Seed an employee qualified for this care — resolveEmployee requires
            // at least one active employee with the care in their assignedCares set.
            Employee employee = new Employee();
            employee.setName("Test Employee");
            employee.setEmail("employee-concurrency@test.com");
            employee.setUserId(ownerUserId);
            employee.setActive(true);
            employee.setAssignedCares(java.util.Set.of(care));
            employee = employeeRepository.saveAndFlush(employee);
            employeeId = employee.getId();

            // Pick a weekday ~two weeks out so both min-advance (120 min) and
            // max-advance (90 days) tenant limits are satisfied.
            appointmentDate = LocalDate.now().plusDays(14);
            while (appointmentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                    || appointmentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                appointmentDate = appointmentDate.plusDays(1);
            }
            appointmentTime = LocalTime.of(10, 0);

            // Opening hours covering that weekday (salon-wide, employee_id null)
            OpeningHour oh = new OpeningHour();
            oh.setDayOfWeek(appointmentDate.getDayOfWeek().getValue());
            oh.setOpenTime(LocalTime.of(9, 0));
            oh.setCloseTime(LocalTime.of(18, 0));
            openingHourRepository.save(oh);

            // Previously this test pre-seeded a SalonClient with a dummy phone to
            // avoid the NOT NULL integrity violation triggered by the service's
            // auto-create path. With SalonClient.phone now nullable, the booking
            // service can create the SalonClient itself — no workaround needed.
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void twoConcurrentBookingsForSameSlot_onlyOneSucceeds() throws Exception {
        // Both threads target the same employee so UK_BOOKING_SLOT_EMPLOYEE arbitrates the race.
        ClientBookingRequest request = new ClientBookingRequest(
                careId,
                appointmentDate,
                appointmentTime.toString(),
                employeeId
        );

        User client = loadUser(clientUserId);
        User owner = loadUser(ownerUserId);

        // Two threads, started simultaneously via a barrier, both trying the same slot.
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                TenantContext.setCurrentTenant(TENANT_SLUG);
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    careBookingService.createClientBooking(client, owner, "Concurrency Salon", request);
                    return Outcome.success();
                } catch (ResponseStatusException rse) {
                    return Outcome.rejected(rse);
                } catch (Exception other) {
                    return Outcome.error(other);
                } finally {
                    TenantContext.clear();
                }
            });
        }

        List<Future<Outcome>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();
        for (Future<Outcome> f : futures) {
            Outcome o = f.get();
            switch (o.kind) {
                case SUCCESS -> successes.incrementAndGet();
                case CONFLICT -> conflicts.incrementAndGet();
                case ERROR -> unexpected.add(o.cause);
            }
        }

        assertThat(unexpected)
                .as("unexpected errors raised by the booking service")
                .isEmpty();
        assertThat(successes.get())
                .as("exactly one booking should succeed under racing inserts")
                .isEqualTo(1);
        assertThat(conflicts.get())
                .as("the losing thread must surface a 409 CONFLICT")
                .isEqualTo(1);

        // Query persisted rows under the tenant schema — only one must exist.
        TenantContext.setCurrentTenant(TENANT_SLUG);
        try {
            long count = careBookingRepository.findByAppointmentDateAndStatusNot(
                    appointmentDate,
                    com.luxpretty.app.bookings.domain.CareBookingStatus.CANCELLED
            ).size();
            assertThat(count)
                    .as("database must hold exactly one non-cancelled booking for the contested slot")
                    .isEqualTo(1L);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    User loadUser(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    private record Outcome(Kind kind, Throwable cause) {
        enum Kind { SUCCESS, CONFLICT, ERROR }
        static Outcome success() { return new Outcome(Kind.SUCCESS, null); }
        static Outcome rejected(ResponseStatusException rse) { return new Outcome(Kind.CONFLICT, rse); }
        static Outcome error(Throwable t) { return new Outcome(Kind.ERROR, t); }
    }
}
