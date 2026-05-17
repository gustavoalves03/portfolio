package com.luxpretty.app.bookings;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link CareBookingRepository#findRemindersDueOnDate(LocalDate)}.
 *
 * <p>Closes a coverage gap from the Phase 5 review of the J-1 reminder feature:
 * scheduler unit tests stub this repository method with Mockito, so a JPQL typo
 * (wrong column, wrong enum FQN, etc.) would compile and ship without failing
 * any test. The query is parsed at runtime, not compile time.
 *
 * <p>Uses {@code @DataJpaTest} with H2 (same pattern as
 * {@code BookingServiceLimitsIntegrationTests}) so the real JPQL is executed
 * against a live database.
 */
@DataJpaTest
class CareBookingRepositoryReminderTests {

    @Autowired UserRepository userRepo;
    @Autowired CareRepository careRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired CareBookingRepository bookingRepo;

    private User client;
    private Care care;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        client = userRepo.save(User.builder()
                .email("reminder-client@test.com")
                .name("Reminder Client")
                .provider(AuthProvider.LOCAL)
                .build());

        Category cat = new Category();
        cat.setName("Visage");
        cat = categoryRepo.save(cat);

        care = new Care();
        care.setName("Soin reminder");
        care.setPrice(80);
        care.setDescription("desc");
        care.setStatus(CareStatus.ACTIVE);
        care.setDuration(60);
        care.setCategory(cat);
        care = careRepo.save(care);

        tomorrow = LocalDate.now().plusDays(1);
    }

    private CareBooking persistBooking(
            LocalDate date,
            LocalTime time,
            CareBookingStatus status,
            Instant reminderSentAt) {
        CareBooking b = new CareBooking();
        b.setUser(client);
        b.setCare(care);
        b.setQuantity(1);
        b.setAppointmentDate(date);
        b.setAppointmentTime(time);
        b.setStatus(status);
        b.setReminderSentAt(reminderSentAt);
        b.setCreatedAt(LocalDateTime.now());
        return bookingRepo.save(b);
    }

    @Test
    void returnsConfirmedBookingDueTomorrowWithNullReminder() {
        // GIVEN a CONFIRMED booking due tomorrow, reminder not yet sent
        CareBooking due = persistBooking(
                tomorrow, LocalTime.of(10, 0), CareBookingStatus.CONFIRMED, null);
        // AND a CONFIRMED booking on a different date (to ensure narrow filter)
        persistBooking(
                tomorrow.plusDays(2), LocalTime.of(11, 0), CareBookingStatus.CONFIRMED, null);

        // WHEN
        List<CareBooking> result = bookingRepo.findRemindersDueOnDate(tomorrow);

        // THEN only the matching booking is returned
        assertThat(result).extracting(CareBooking::getId).containsExactly(due.getId());
    }

    @Test
    void skipsCancelledBookingOnSameDate() {
        // GIVEN one CONFIRMED (matching) and one CANCELLED on the same date
        CareBooking confirmed = persistBooking(
                tomorrow, LocalTime.of(10, 0), CareBookingStatus.CONFIRMED, null);
        persistBooking(
                tomorrow, LocalTime.of(11, 0), CareBookingStatus.CANCELLED, null);

        // WHEN
        List<CareBooking> result = bookingRepo.findRemindersDueOnDate(tomorrow);

        // THEN cancelled booking is excluded
        assertThat(result).extracting(CareBooking::getId).containsExactly(confirmed.getId());
    }

    @Test
    void skipsBookingWithReminderAlreadySent() {
        // GIVEN one CONFIRMED reminder-not-sent and one CONFIRMED reminder-already-sent
        CareBooking pending = persistBooking(
                tomorrow, LocalTime.of(10, 0), CareBookingStatus.CONFIRMED, null);
        persistBooking(
                tomorrow, LocalTime.of(11, 0), CareBookingStatus.CONFIRMED, Instant.now());

        // WHEN
        List<CareBooking> result = bookingRepo.findRemindersDueOnDate(tomorrow);

        // THEN already-reminded booking is excluded
        assertThat(result).extracting(CareBooking::getId).containsExactly(pending.getId());
    }

    @Test
    void skipsBookingOnDifferentDate() {
        // GIVEN one CONFIRMED on tomorrow and one CONFIRMED on the day after
        CareBooking onTarget = persistBooking(
                tomorrow, LocalTime.of(10, 0), CareBookingStatus.CONFIRMED, null);
        persistBooking(
                tomorrow.plusDays(1), LocalTime.of(11, 0), CareBookingStatus.CONFIRMED, null);

        // WHEN
        List<CareBooking> result = bookingRepo.findRemindersDueOnDate(tomorrow);

        // THEN only the booking on the target date is returned
        assertThat(result).extracting(CareBooking::getId).containsExactly(onTarget.getId());
    }
}
