package com.luxpretty.app.bookings;

import com.luxpretty.app.bookings.app.BookingReminderScheduler;
import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingReminderVars;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the J-1 booking reminder scheduler.
 *
 * <p>We use plain Mockito (no Spring context) because the scheduler logic is
 * essentially a TenantContext loop + a per-tenant query + an outbox queue
 * call. The interesting behaviours (status / reminderSentAt / anti-spam
 * window) are all data-driven and easy to express with stubbed repositories.
 */
@ExtendWith(MockitoExtension.class)
class BookingReminderSchedulerTests {

    @Mock private TenantRepository tenantRepository;
    @Mock private CareBookingRepository bookingRepo;
    @Mock private UserRepository userRepository;
    @Mock private CareRepository careRepository;
    @Mock private MailOutboxService mailOutbox;

    private BookingReminderScheduler scheduler;

    private Tenant tenant;
    private User client;
    private Care care;

    @BeforeEach
    void setUp() {
        scheduler = new BookingReminderScheduler(
                tenantRepository, bookingRepo, userRepository, careRepository,
                mailOutbox, "http://localhost:4200");

        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("acme");
        tenant.setName("Acme Salon");
        tenant.setAddressStreet("1 rue de la Paix");
        tenant.setAddressPostalCode("75002");
        tenant.setAddressCity("Paris");

        client = new User();
        client.setId(10L);
        client.setName("Alice");
        client.setEmail("alice@example.com");

        care = new Care();
        care.setId(20L);
        care.setName("Soin visage");
        care.setStatus(CareStatus.ACTIVE);
        care.setPrice(60);
        care.setDuration(30);
    }

    @AfterEach
    void tearDown() {
        // Defensive: scheduler should always clear TenantContext, but ensure
        // a failing test cannot pollute subsequent ones.
        com.luxpretty.app.multitenancy.TenantContext.clear();
    }

    private CareBooking newBooking(LocalDateTime createdAt,
                                   CareBookingStatus status,
                                   java.time.Instant reminderSentAt) {
        CareBooking b = new CareBooking();
        b.setId(100L);
        b.setUser(client);
        b.setCare(care);
        b.setQuantity(1);
        b.setAppointmentDate(LocalDate.now().plusDays(1));
        b.setAppointmentTime(LocalTime.of(14, 30));
        b.setStatus(status);
        b.setCreatedAt(createdAt);
        b.setReminderSentAt(reminderSentAt);
        return b;
    }

    @Test
    void sendReminders_bookingDueTomorrow_queuesMail() {
        CareBooking booking = newBooking(
                LocalDateTime.now().minusDays(3),
                CareBookingStatus.CONFIRMED,
                null);

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(bookingRepo.findRemindersDueOnDate(LocalDate.now().plusDays(1)))
                .thenReturn(List.of(booking));

        scheduler.sendReminders();

        verify(mailOutbox, times(1)).queue(
                eq(MailTemplate.BOOKING_REMINDER_J1),
                any(BookingReminderVars.class),
                eq(client.getEmail()),
                eq(tenant.getSlug()));
        verify(bookingRepo).save(booking);
    }
}
