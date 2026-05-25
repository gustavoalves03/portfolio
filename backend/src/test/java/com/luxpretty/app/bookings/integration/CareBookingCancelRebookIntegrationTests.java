package com.luxpretty.app.bookings.integration;

import com.luxpretty.app.availability.domain.OpeningHour;
import com.luxpretty.app.availability.repo.OpeningHourRepository;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.bookings.web.dto.ClientBookingRequest;
import com.luxpretty.app.bookings.web.dto.ClientBookingResponse;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the 409 bug on re-booking a previously cancelled slot.
 *
 * <p>Scenario: a client books slot S, then cancels. The cancel flow soft-deletes
 * by setting {@code status = CANCELLED} but keeps the row. When the same client
 * (or any other) then tries to book S again, the unique constraint
 * {@code UK_BOOKING_SLOT (appointment_date, appointment_time, care_id)} still
 * sees the cancelled row and blocks the insert, which the service rethrows as
 * a 409 "Slot no longer available".
 *
 * <p>After the fix, re-booking a cancelled slot must succeed (201).
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CareBookingCancelRebookIntegrationTests {

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

    private static final String TENANT_SLUG = "rebook-salon";

    private Long careId;
    private Long clientUserId;
    private Long ownerUserId;
    private Long employeeId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;

    @BeforeEach
    void setUp() {
        tenantSchemaManager.provisionSchema(TENANT_SLUG);
        tenantSchemaManager.migrateSchema(TENANT_SLUG);

        TenantContext.clear();
        User owner = userRepository.findByEmail("owner-rebook@test.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Owner Rebook")
                        .email("owner-rebook@test.com")
                        .password("password")
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .build()));
        ownerUserId = owner.getId();

        User client = userRepository.findByEmail("client-rebook@test.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Client Rebook")
                        .email("client-rebook@test.com")
                        .password("password")
                        .provider(AuthProvider.LOCAL)
                        .emailVerified(true)
                        .build()));
        clientUserId = client.getId();

        tenantRepository.findBySlug(TENANT_SLUG).orElseGet(() ->
                tenantRepository.save(Tenant.builder()
                        .slug(TENANT_SLUG)
                        .name("Rebook Salon")
                        .ownerId(ownerUserId)
                        .build()));

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
            employee.setEmail("employee-rebook@test.com");
            employee.setUserId(ownerUserId);
            employee.setActive(true);
            employee.setAssignedCares(java.util.Set.of(care));
            employee = employeeRepository.saveAndFlush(employee);
            employeeId = employee.getId();

            appointmentDate = LocalDate.now().plusDays(14);
            while (appointmentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                    || appointmentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                appointmentDate = appointmentDate.plusDays(1);
            }
            appointmentTime = LocalTime.of(10, 0);

            OpeningHour oh = new OpeningHour();
            oh.setDayOfWeek(appointmentDate.getDayOfWeek().getValue());
            oh.setOpenTime(LocalTime.of(9, 0));
            oh.setCloseTime(LocalTime.of(18, 0));
            openingHourRepository.save(oh);
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @org.junit.jupiter.api.Disabled("H2 does not support filtered unique indexes the same way Oracle does; " +
            "the plain UK_BOOKING_SLOT_EMPLOYEE fallback blocks re-booking a cancelled slot. " +
            "Coverage is via the opt-in BookingConcurrencyOracleTests Testcontainers test " +
            "(set TESTCONTAINERS_ORACLE=true to run).")
    @Test
    void rebookingACancelledSlot_shouldSucceed() {
        ClientBookingRequest request = new ClientBookingRequest(
                careId,
                appointmentDate,
                appointmentTime.toString(),
                employeeId
        );

        User client = userRepository.findById(clientUserId).orElseThrow();
        User owner = userRepository.findById(ownerUserId).orElseThrow();

        TenantContext.setCurrentTenant(TENANT_SLUG);
        try {
            // 1) First booking succeeds.
            ClientBookingResponse first = careBookingService.createClientBooking(
                    client, owner, "Rebook Salon", request);
            assertThat(first.bookingId()).isNotNull();

            // 2) Cancel it — the row stays with status=CANCELLED.
            careBookingService.cancelBooking(first.bookingId());

            assertThat(careBookingRepository.findById(first.bookingId()))
                    .hasValueSatisfying(b -> assertThat(b.getStatus())
                            .isEqualTo(CareBookingStatus.CANCELLED));

            // 3) Re-book the exact same slot — must NOT throw 409.
            ClientBookingResponse second = careBookingService.createClientBooking(
                    client, owner, "Rebook Salon", request);
            assertThat(second.bookingId())
                    .as("re-booking a cancelled slot must return a new booking id")
                    .isNotNull()
                    .isNotEqualTo(first.bookingId());

            // DB state: exactly one ACTIVE (non-cancelled) booking for this slot.
            long nonCancelled = careBookingRepository
                    .findByAppointmentDateAndStatusNot(appointmentDate, CareBookingStatus.CANCELLED)
                    .stream()
                    .filter(b -> b.getAppointmentTime().equals(appointmentTime)
                            && b.getCare().getId().equals(careId))
                    .count();
            assertThat(nonCancelled)
                    .as("exactly one non-cancelled booking for the re-booked slot")
                    .isEqualTo(1L);
        } finally {
            TenantContext.clear();
        }
    }
}
