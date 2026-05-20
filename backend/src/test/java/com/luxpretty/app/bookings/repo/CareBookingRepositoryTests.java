package com.luxpretty.app.bookings.repo;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CareBookingRepositoryTests {

    @Autowired CareBookingRepository repo;
    @Autowired UserRepository userRepo;
    @Autowired CareRepository careRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired EmployeeRepository employeeRepo;

    private static final AtomicLong USER_COUNTER = new AtomicLong(0);
    // Static counter is intentional: @DataJpaTest rolls back rows per test, but
    // the JVM-wide counter survives so each test gets a distinct userId, avoiding
    // UK_EMPLOYEE_USER collisions between successive tests within the same run.
    private static final AtomicLong USER_ID_SEQ = new AtomicLong(1_000_000L);

    private User persistMinimalUser() {
        long n = USER_COUNTER.incrementAndGet();
        return userRepo.save(User.builder()
                .email("test-booking-user-" + n + "@test.com")
                .name("Test User " + n)
                .provider(AuthProvider.LOCAL)
                .build());
    }

    private Employee persistEmployee(String name) {
        long userId = USER_ID_SEQ.getAndIncrement();
        Employee e = new Employee();
        e.setUserId(userId);
        e.setName(name);
        e.setEmail(name.toLowerCase() + "-" + userId + "@test.com");
        return employeeRepo.save(e);
    }

    private Care persistMinimalCare() {
        Category cat = new Category();
        cat.setName("Test Category");
        cat = categoryRepo.save(cat);

        Care care = new Care();
        care.setName("Test Care");
        care.setPrice(50);
        care.setDescription("desc");
        care.setStatus(CareStatus.ACTIVE);
        care.setDuration(30);
        care.setCategory(cat);
        return careRepo.save(care);
    }

    private CareBooking saveBooking(Employee employee, User user, Care care,
                                    LocalDate date, LocalTime time, CareBookingStatus status) {
        CareBooking b = new CareBooking();
        b.setUser(user);
        b.setCare(care);
        b.setStatus(status);
        b.setAppointmentDate(date);
        b.setAppointmentTime(time);
        b.setQuantity(1);
        b.setEmployeeId(employee.getId());
        return repo.saveAndFlush(b);
    }

    @Test
    void persistsCancellationReason() {
        User u = persistMinimalUser();
        Care c = persistMinimalCare();
        CareBooking b = new CareBooking();
        b.setUser(u);
        b.setCare(c);
        b.setStatus(CareBookingStatus.CANCELLED);
        b.setCancellationReason("test reason");
        b.setAppointmentDate(LocalDate.of(2026, 6, 1));
        b.setAppointmentTime(LocalTime.of(10, 0));
        b.setQuantity(1);
        CareBooking saved = repo.saveAndFlush(b);
        CareBooking reloaded = repo.findById(saved.getId()).orElseThrow();
        assertEquals("test reason", reloaded.getCancellationReason());
    }

    @Test
    void countActiveByEmployeeAndDate_excludesCancelled() {
        Employee marie = persistEmployee("Marie");
        User user = persistMinimalUser();
        Care care = persistMinimalCare();
        saveBooking(marie, user, care, LocalDate.of(2026, 6, 1), LocalTime.of(10, 0), CareBookingStatus.CONFIRMED);
        saveBooking(marie, user, care, LocalDate.of(2026, 6, 1), LocalTime.of(11, 0), CareBookingStatus.PENDING);
        saveBooking(marie, user, care, LocalDate.of(2026, 6, 1), LocalTime.of(12, 0), CareBookingStatus.CANCELLED);

        Map<Long, Long> counts = repo.countActiveByEmployeeAndDate(LocalDate.of(2026, 6, 1)).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
        assertEquals(2L, counts.get(marie.getId())); // CONFIRMED + PENDING; CANCELLED excluded
    }

    @Test
    void findActiveByDateAndEmployees_returnsOnlyActiveStatuses() {
        Employee marie = persistEmployee("Marie");
        User user = persistMinimalUser();
        Care care = persistMinimalCare();
        saveBooking(marie, user, care, LocalDate.of(2026, 6, 1), LocalTime.of(10, 0), CareBookingStatus.CONFIRMED);
        saveBooking(marie, user, care, LocalDate.of(2026, 6, 1), LocalTime.of(11, 0), CareBookingStatus.CANCELLED);

        List<CareBooking> rows = repo.findActiveByDateAndEmployees(
                LocalDate.of(2026, 6, 1), List.of(marie.getId()));
        assertEquals(1, rows.size());
        assertEquals(LocalTime.of(10, 0), rows.get(0).getAppointmentTime());
    }

    @Test
    void findActiveByDateAndEmployees_excludesEmployeesNotInList() {
        Employee marie = persistEmployee("Marie");
        Employee sophie = persistEmployee("Sophie");
        User user = persistMinimalUser();
        Care care = persistMinimalCare();
        saveBooking(sophie, user, care, LocalDate.of(2026, 6, 1), LocalTime.of(10, 0), CareBookingStatus.CONFIRMED);

        List<CareBooking> rows = repo.findActiveByDateAndEmployees(
                LocalDate.of(2026, 6, 1), List.of(marie.getId()));
        assertTrue(rows.isEmpty());
    }
}
