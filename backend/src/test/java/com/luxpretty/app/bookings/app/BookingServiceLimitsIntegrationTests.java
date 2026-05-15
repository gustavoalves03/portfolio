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
import com.luxpretty.app.users.domain.AuthProvider;
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
        client = User.builder()
                .email("alice@test.com")
                .name("Alice")
                .provider(AuthProvider.LOCAL)
                .build();
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
