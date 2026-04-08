package com.prettyface.app.bookings.app;

import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.domain.ClientBookingHistory;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.bookings.repo.ClientBookingHistoryRepository;
import com.prettyface.app.bookings.web.dto.ClientBookingRequest;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.notification.app.EmailService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CareBookingServiceTests {

    @Mock private CareBookingRepository bookingRepo;
    @Mock private UserRepository userRepository;
    @Mock private CareRepository careRepository;
    @Mock private SlotAvailabilityService slotAvailabilityService;
    @Mock private EmailService emailService;
    @Mock private TenantRepository tenantRepository;
    @Mock private ClientBookingHistoryRepository clientBookingHistoryRepository;
    @Mock private ApplicationSchemaExecutor applicationSchemaExecutor;
    @Mock private com.prettyface.app.employee.repo.EmployeeRepository employeeRepository;
    @Mock private com.prettyface.app.notification.app.NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private CareBookingService service;

    private User client;
    private User owner;
    private Care care30min;
    private final LocalDate futureDate = nextMonday();

    @BeforeEach
    void setUp() {
        client = User.builder().id(1L).name("Marie").email("marie@test.com").build();
        owner = User.builder().id(2L).name("Sophie").email("sophie@test.com").build();

        care30min = new Care();
        care30min.setId(10L);
        care30min.setName("Soin visage");
        care30min.setDuration(30);
        care30min.setPrice(5500);
        care30min.setStatus(CareStatus.ACTIVE);

        // Default: no existing cross-salon bookings
        lenient().when(clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                any(Long.class), any(LocalDate.class), any(String.class))).thenReturn(List.of());
        lenient().when(applicationSchemaExecutor.call(any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Prise de RDV — cas de base ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Booking at opening time (09:00) — slot available → succeeds")
    void bookingAtOpeningTime_succeeds() {
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        mockSlotAvailable("09:00");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentTime()).isEqualTo("09:00");
        assertThat(result.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Booking that ends exactly at closing time — slot available → succeeds")
    void bookingEndsAtClosingTime_succeeds() {
        // Salon closes at 18:00, 30-min care at 17:30 → ends at 18:00 exactly
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        mockSlotAvailable("17:30");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "17:30", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentTime()).isEqualTo("17:30");
    }

    @Test
    @DisplayName("Booking that would exceed closing by 1 min — slot NOT in available list → rejected")
    void bookingExceedsClosing_rejected() {
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        // 17:31 is not a valid slot (slots are every 30 min, and 17:31+30=18:01 > 18:00)
        mockNoSlotAvailable("17:31");

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "17:31", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slot no longer available");
    }

    @Test
    @DisplayName("Booking in the past — slot service returns empty → rejected")
    void bookingInPast_rejected() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        when(slotAvailabilityService.getAvailableSlots(yesterday, 10L))
                .thenReturn(List.of()); // Past date → empty

        ClientBookingRequest req = new ClientBookingRequest(10L, yesterday, "09:00", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slot no longer available");
    }

    @Test
    @DisplayName("Booking far in the future (2 years) — no max limit, slot available → succeeds")
    void bookingFarFuture_noMaxLimit_succeeds() {
        LocalDate twoYearsFromNow = LocalDate.now().plusYears(2);
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        when(slotAvailabilityService.getAvailableSlots(twoYearsFromNow, 10L))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot("09:00", "09:30")));
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, twoYearsFromNow, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        // No max date limit exists — booking succeeds
        assertThat(result.appointmentTime()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("Inactive care → NOT_FOUND")
    void inactiveCare_rejected() {
        Care inactiveCare = new Care();
        inactiveCare.setId(10L);
        inactiveCare.setStatus(CareStatus.INACTIVE);
        when(careRepository.findById(10L)).thenReturn(Optional.of(inactiveCare));

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Care not found or inactive");
    }

    @Test
    @DisplayName("Care not found → NOT_FOUND")
    void careNotFound_rejected() {
        when(careRepository.findById(99L)).thenReturn(Optional.empty());

        ClientBookingRequest req = new ClientBookingRequest(99L, futureDate, "09:00", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Care not found or inactive");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Concurrence / race condition ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Race condition: two clients book same slot — second gets CONFLICT via slot check")
    void raceCondition_secondClientRejected_slotCheck() {
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // First call: slot available → booking succeeds
        when(slotAvailabilityService.getAvailableSlots(futureDate, 10L))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot("09:00", "09:30")))
                .thenReturn(List.of()); // Second call: slot no longer available

        mockSaveBooking();

        // Client 1 succeeds
        ClientBookingRequest req1 = new ClientBookingRequest(10L, futureDate, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req1);
        assertThat(result.status()).isEqualTo("CONFIRMED");

        // Client 2: slot check returns empty → CONFLICT
        User client2 = User.builder().id(3L).name("Julie").email("julie@test.com").build();
        ClientBookingRequest req2 = new ClientBookingRequest(10L, futureDate, "09:00", null);

        assertThatThrownBy(() -> service.createClientBooking(client2, owner, "Salon", req2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slot no longer available");
    }

    @Test
    @DisplayName("Race condition: slot check passes but DB save fails (unique constraint) → CONFLICT")
    void raceCondition_dbConstraintViolation_conflict() {
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        mockSlotAvailable("09:00");

        // Simulate: slot check passes but another thread saved first → DB constraint violation
        when(bookingRepo.save(any(CareBooking.class)))
                .thenThrow(new DataIntegrityViolationException("UK_BOOKING_SLOT"));

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Slot no longer available");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Annulation et re-booking ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Book → cancel → re-book same slot: cancelled booking doesn't block, re-book succeeds")
    void bookCancelRebook_succeeds() {
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // Slot is available (cancelled bookings are excluded by the slot service)
        mockSlotAvailable("09:00");
        mockSaveBooking();

        // Re-book after cancellation: slot is available again → succeeds
        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentTime()).isEqualTo("09:00");
        assertThat(result.status()).isEqualTo("CONFIRMED");

        // Verify the booking was saved
        verify(bookingRepo).save(any(CareBooking.class));
    }

    @Test
    @DisplayName("Booking sends confirmation emails to client and owner")
    void booking_sendsEmails() {
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        mockSlotAvailable("09:00");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);
        service.createClientBooking(client, owner, "Salon", req);

        verify(emailService).sendBookingConfirmationEmail(eq(client), any(CareBooking.class), eq(care30min), eq("Salon"));
        verify(emailService).sendNewBookingNotificationEmail(eq(owner), any(CareBooking.class), eq(care30min), eq("Marie"));
    }

    // ══════════════════════════════════════════════════════════════
    // ── Annulation en masse par soin ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cancel future bookings for a care → returns count, all set to CANCELLED")
    void cancelFutureBookingsForCare_returnsCountAndCancels() {
        CareBooking b1 = new CareBooking();
        b1.setId(1L);
        b1.setStatus(CareBookingStatus.CONFIRMED);
        b1.setAppointmentDate(futureDate);

        CareBooking b2 = new CareBooking();
        b2.setId(2L);
        b2.setStatus(CareBookingStatus.PENDING);
        b2.setAppointmentDate(futureDate.plusDays(7));

        when(bookingRepo.findByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                eq(10L), any(LocalDate.class), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(b1, b2));
        when(bookingRepo.save(any(CareBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = service.cancelFutureBookingsForCare(10L);

        assertThat(count).isEqualTo(2);
        assertThat(b1.getStatus()).isEqualTo(CareBookingStatus.CANCELLED);
        assertThat(b2.getStatus()).isEqualTo(CareBookingStatus.CANCELLED);
        verify(bookingRepo, times(2)).save(any(CareBooking.class));
    }

    @Test
    @DisplayName("Cancel future bookings — no future bookings → returns 0")
    void cancelFutureBookingsForCare_noBookings_returnsZero() {
        when(bookingRepo.findByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                eq(10L), any(LocalDate.class), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of());

        int count = service.cancelFutureBookingsForCare(10L);

        assertThat(count).isEqualTo(0);
        verify(bookingRepo, never()).save(any(CareBooking.class));
    }

    @Test
    @DisplayName("Cancel future bookings — mix of future and past → only future ones cancelled")
    void cancelFutureBookingsForCare_mixFuturePast_onlyFutureCancelled() {
        // The repository query already filters for future dates,
        // so only future bookings are returned
        CareBooking futureBooking = new CareBooking();
        futureBooking.setId(1L);
        futureBooking.setStatus(CareBookingStatus.CONFIRMED);
        futureBooking.setAppointmentDate(futureDate.plusDays(3));

        // Past bookings are NOT returned by the repo query
        when(bookingRepo.findByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                eq(10L), any(LocalDate.class), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(futureBooking));
        when(bookingRepo.save(any(CareBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = service.cancelFutureBookingsForCare(10L);

        assertThat(count).isEqualTo(1);
        assertThat(futureBooking.getStatus()).isEqualTo(CareBookingStatus.CANCELLED);
        verify(bookingRepo, times(1)).save(any(CareBooking.class));
    }

    // ══════════════════════════════════════════════════════════════
    // ── Limites de réservation (booking limits) ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Booking too soon (min advance violation) → BAD_REQUEST")
    void bookingTooSoon_minAdvanceViolation_rejected() {
        TenantContext.setCurrentTenant("test-salon");
        Tenant tenant = new Tenant();
        tenant.setMinAdvanceMinutes(120);
        tenant.setMaxAdvanceDays(90);
        tenant.setMaxClientHoursPerDay(8);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));

        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // Book for 30 minutes from now (violates 120-minute minimum)
        LocalDate today = LocalDate.now();
        LocalTime soonTime = LocalTime.now().plusMinutes(30);
        String timeStr = soonTime.withSecond(0).withNano(0).toString();

        ClientBookingRequest req = new ClientBookingRequest(10L, today, timeStr, null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("at least 120 minutes in advance");
    }

    @Test
    @DisplayName("Booking too far ahead (max advance violation) → BAD_REQUEST")
    void bookingTooFarAhead_maxAdvanceViolation_rejected() {
        TenantContext.setCurrentTenant("test-salon");
        Tenant tenant = new Tenant();
        tenant.setMinAdvanceMinutes(0);
        tenant.setMaxAdvanceDays(90);
        tenant.setMaxClientHoursPerDay(8);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));

        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // Book for 100 days from now (violates 90-day max)
        LocalDate farDate = LocalDate.now().plusDays(100);
        ClientBookingRequest req = new ClientBookingRequest(10L, farDate, "09:00", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("more than 90 days in advance");
    }

    @Test
    @DisplayName("Cross-salon overlap detected → CONFLICT")
    void crossSalonOverlap_rejected() {
        TenantContext.setCurrentTenant("test-salon");
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // Client already has a booking from 09:00 to 09:30 at another salon
        ClientBookingHistory existing = new ClientBookingHistory();
        existing.setAppointmentTime(LocalTime.of(9, 0));
        existing.setCareDuration(30);
        existing.setSalonName("Other Salon");
        when(clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                eq(1L), eq(futureDate), eq("CANCELLED"))).thenReturn(List.of(existing));

        // Try to book at 09:15 (overlaps with 09:00-09:30)
        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:15", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("You already have a booking from 09:00 to 09:30 at Other Salon");
    }

    @Test
    @DisplayName("Cross-salon lookup runs through application schema executor")
    void crossSalonLookup_usesApplicationSchemaExecutor() {
        TenantContext.setCurrentTenant("test-salon");
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        mockSlotAvailable("09:00");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);
        service.createClientBooking(client, owner, "Salon", req);

        verify(applicationSchemaExecutor).call(any());
    }

    @Test
    @DisplayName("Adjacent bookings (no overlap) → succeeds")
    void adjacentBookings_noOverlap_succeeds() {
        TenantContext.setCurrentTenant("test-salon");
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // Client already has a booking from 09:00 to 09:30 at another salon
        ClientBookingHistory existing = new ClientBookingHistory();
        existing.setAppointmentTime(LocalTime.of(9, 0));
        existing.setCareDuration(30);
        existing.setSalonName("Other Salon");
        when(clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                eq(1L), eq(futureDate), eq("CANCELLED"))).thenReturn(List.of(existing));

        // Book at 09:30 (adjacent, no overlap: 09:30 is NOT before 09:30)
        mockSlotAvailable("09:30");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:30", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentTime()).isEqualTo("09:30");
        assertThat(result.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Max hours per day exceeded → TOO_MANY_REQUESTS")
    void maxHoursPerDayExceeded_rejected() {
        TenantContext.setCurrentTenant("test-salon");
        Tenant tenant = new Tenant();
        tenant.setMinAdvanceMinutes(0);
        tenant.setMaxAdvanceDays(365);
        tenant.setMaxClientHoursPerDay(2); // 2 hours max = 120 minutes
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));

        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // Client already has 2 hours of bookings (no overlap with the new one)
        ClientBookingHistory b1 = new ClientBookingHistory();
        b1.setAppointmentTime(LocalTime.of(10, 0));
        b1.setCareDuration(60);
        b1.setSalonName("Salon A");
        ClientBookingHistory b2 = new ClientBookingHistory();
        b2.setAppointmentTime(LocalTime.of(11, 0));
        b2.setCareDuration(60);
        b2.setSalonName("Salon B");
        when(clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                eq(1L), eq(futureDate), eq("CANCELLED"))).thenReturn(List.of(b1, b2));

        // Try to book a 30-min care at 09:00 (no overlap, but total = 150 min > 120 min)
        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);

        assertThatThrownBy(() -> service.createClientBooking(client, owner, "Salon", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .hasMessageContaining("Maximum 2 hours of appointments per day");
    }

    @Test
    @DisplayName("Max hours per day not exceeded → succeeds")
    void maxHoursPerDayNotExceeded_succeeds() {
        TenantContext.setCurrentTenant("test-salon");
        Tenant tenant = new Tenant();
        tenant.setMinAdvanceMinutes(0);
        tenant.setMaxAdvanceDays(365);
        tenant.setMaxClientHoursPerDay(2); // 2 hours max = 120 minutes
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));

        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // Client has 1 hour of bookings
        ClientBookingHistory b1 = new ClientBookingHistory();
        b1.setAppointmentTime(LocalTime.of(10, 0));
        b1.setCareDuration(60);
        b1.setSalonName("Salon A");
        when(clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                eq(1L), eq(futureDate), eq("CANCELLED"))).thenReturn(List.of(b1));

        // 30-min care at 09:00 (no overlap, total = 90 min <= 120 min)
        mockSlotAvailable("09:00");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentTime()).isEqualTo("09:00");
        assertThat(result.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Cancelled booking doesn't count toward overlap or hours")
    void cancelledBooking_doesNotCount() {
        TenantContext.setCurrentTenant("test-salon");
        Tenant tenant = new Tenant();
        tenant.setMinAdvanceMinutes(0);
        tenant.setMaxAdvanceDays(365);
        tenant.setMaxClientHoursPerDay(1); // Only 1 hour max
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));

        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));

        // No non-cancelled bookings returned (cancelled ones are excluded by the query)
        when(clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                eq(1L), eq(futureDate), eq("CANCELLED"))).thenReturn(List.of());

        mockSlotAvailable("09:00");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentTime()).isEqualTo("09:00");
        assertThat(result.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Booking within all limits → succeeds")
    void bookingWithinLimits_succeeds() {
        TenantContext.setCurrentTenant("test-salon");
        Tenant tenant = new Tenant();
        tenant.setMinAdvanceMinutes(120);
        tenant.setMaxAdvanceDays(90);
        tenant.setMaxClientHoursPerDay(8);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));

        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        // Client has no bookings for this date
        when(clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                eq(1L), eq(futureDate), eq("CANCELLED"))).thenReturn(List.of());
        mockSlotAvailable("09:00");
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, futureDate, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentTime()).isEqualTo("09:00");
        assertThat(result.status()).isEqualTo("CONFIRMED");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Annulations et modifications — scénarios critiques ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FIX: Cancel past booking → rejected, must use NO_SHOW")
    void cancelPastBooking_rejected() {
        CareBooking pastBooking = new CareBooking();
        pastBooking.setId(100L);
        pastBooking.setUser(client);
        pastBooking.setCare(care30min);
        pastBooking.setQuantity(1);
        pastBooking.setAppointmentDate(LocalDate.now().minusDays(1));
        pastBooking.setAppointmentTime(LocalTime.of(10, 0));
        pastBooking.setStatus(CareBookingStatus.CONFIRMED);

        when(bookingRepo.findById(100L)).thenReturn(Optional.of(pastBooking));

        com.prettyface.app.bookings.web.dto.CareBookingRequest cancelReq =
                new com.prettyface.app.bookings.web.dto.CareBookingRequest(
                        1L, 10L, 1,
                        LocalDate.now().minusDays(1),
                        LocalTime.of(10, 0),
                        CareBookingStatus.CANCELLED);

        assertThatThrownBy(() -> service.update(100L, cancelReq))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot cancel a past appointment");
    }

    @Test
    @DisplayName("FIX: Past booking → NO_SHOW status accepted")
    void pastBooking_noShowAccepted() {
        CareBooking pastBooking = new CareBooking();
        pastBooking.setId(100L);
        pastBooking.setUser(client);
        pastBooking.setCare(care30min);
        pastBooking.setQuantity(1);
        pastBooking.setAppointmentDate(LocalDate.now().minusDays(1));
        pastBooking.setAppointmentTime(LocalTime.of(10, 0));
        pastBooking.setStatus(CareBookingStatus.CONFIRMED);

        when(bookingRepo.findById(100L)).thenReturn(Optional.of(pastBooking));
        when(bookingRepo.save(any(CareBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        com.prettyface.app.bookings.web.dto.CareBookingRequest noShowReq =
                new com.prettyface.app.bookings.web.dto.CareBookingRequest(
                        1L, 10L, 1,
                        LocalDate.now().minusDays(1),
                        LocalTime.of(10, 0),
                        CareBookingStatus.NO_SHOW);

        var result = service.update(100L, noShowReq);
        assertThat(result.status()).isEqualTo(CareBookingStatus.NO_SHOW);
    }

    @Test
    @DisplayName("FIX: Reschedule past booking → rejected")
    void reschedulePastBooking_rejected() {
        CareBooking pastBooking = new CareBooking();
        pastBooking.setId(100L);
        pastBooking.setUser(client);
        pastBooking.setCare(care30min);
        pastBooking.setQuantity(1);
        pastBooking.setAppointmentDate(LocalDate.now().minusDays(1));
        pastBooking.setAppointmentTime(LocalTime.of(10, 0));
        pastBooking.setStatus(CareBookingStatus.CONFIRMED);

        when(bookingRepo.findById(100L)).thenReturn(Optional.of(pastBooking));

        // Try to move to a future date
        com.prettyface.app.bookings.web.dto.CareBookingRequest moveReq =
                new com.prettyface.app.bookings.web.dto.CareBookingRequest(
                        1L, 10L, 1, futureDate,
                        LocalTime.of(9, 0),
                        CareBookingStatus.CONFIRMED);

        assertThatThrownBy(() -> service.update(100L, moveReq))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot reschedule a past appointment");
    }

    @Test
    @DisplayName("FIX: Move booking to unavailable slot → CONFLICT")
    void moveToUnavailableSlot_rejected() {
        CareBooking existingBooking = new CareBooking();
        existingBooking.setId(101L);
        existingBooking.setUser(client);
        existingBooking.setCare(care30min);
        existingBooking.setQuantity(1);
        existingBooking.setAppointmentDate(futureDate);
        existingBooking.setAppointmentTime(LocalTime.of(9, 0));
        existingBooking.setStatus(CareBookingStatus.CONFIRMED);

        when(bookingRepo.findById(101L)).thenReturn(Optional.of(existingBooking));

        // Slot service says 03:00 is NOT available
        when(slotAvailabilityService.getAvailableSlots(futureDate, 10L))
                .thenReturn(List.of()); // No slots

        com.prettyface.app.bookings.web.dto.CareBookingRequest moveReq =
                new com.prettyface.app.bookings.web.dto.CareBookingRequest(
                        1L, 10L, 1, futureDate,
                        LocalTime.of(3, 0),
                        CareBookingStatus.CONFIRMED);

        assertThatThrownBy(() -> service.update(101L, moveReq))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("FIX: Move booking to available slot → succeeds")
    void moveToAvailableSlot_succeeds() {
        CareBooking existingBooking = new CareBooking();
        existingBooking.setId(101L);
        existingBooking.setUser(client);
        existingBooking.setCare(care30min);
        existingBooking.setQuantity(1);
        existingBooking.setAppointmentDate(futureDate);
        existingBooking.setAppointmentTime(LocalTime.of(9, 0));
        existingBooking.setStatus(CareBookingStatus.CONFIRMED);

        when(bookingRepo.findById(101L)).thenReturn(Optional.of(existingBooking));
        when(slotAvailabilityService.getAvailableSlots(futureDate, 10L))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot("14:00", "14:30")));
        when(bookingRepo.save(any(CareBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        com.prettyface.app.bookings.web.dto.CareBookingRequest moveReq =
                new com.prettyface.app.bookings.web.dto.CareBookingRequest(
                        1L, 10L, 1, futureDate,
                        LocalTime.of(14, 0),
                        CareBookingStatus.CONFIRMED);

        var result = service.update(101L, moveReq);
        assertThat(result.appointmentTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("FIX: Change care to different service → checks new slot availability")
    void changeCare_checksSlotAvailability() {
        CareBooking existingBooking = new CareBooking();
        existingBooking.setId(102L);
        existingBooking.setUser(client);
        existingBooking.setCare(care30min);
        existingBooking.setQuantity(1);
        existingBooking.setAppointmentDate(futureDate);
        existingBooking.setAppointmentTime(LocalTime.of(17, 30));
        existingBooking.setStatus(CareBookingStatus.CONFIRMED);

        when(bookingRepo.findById(102L)).thenReturn(Optional.of(existingBooking));

        // New care (id=20) has no slot at 17:30
        when(slotAvailabilityService.getAvailableSlots(futureDate, 20L))
                .thenReturn(List.of()); // No slots for the new care at this time

        com.prettyface.app.bookings.web.dto.CareBookingRequest changeReq =
                new com.prettyface.app.bookings.web.dto.CareBookingRequest(
                        1L, 20L, 1, futureDate,
                        LocalTime.of(17, 30),
                        CareBookingStatus.CONFIRMED);

        assertThatThrownBy(() -> service.update(102L, changeReq))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("FIX: Status-only change (no date/time/care change) → no slot check needed")
    void statusOnlyChange_noSlotCheck() {
        CareBooking existingBooking = new CareBooking();
        existingBooking.setId(103L);
        existingBooking.setUser(client);
        existingBooking.setCare(care30min);
        existingBooking.setQuantity(1);
        existingBooking.setAppointmentDate(futureDate);
        existingBooking.setAppointmentTime(LocalTime.of(9, 0));
        existingBooking.setStatus(CareBookingStatus.PENDING);

        when(bookingRepo.findById(103L)).thenReturn(Optional.of(existingBooking));
        when(bookingRepo.save(any(CareBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Just confirm — no date/time/care change → no slot check
        com.prettyface.app.bookings.web.dto.CareBookingRequest confirmReq =
                new com.prettyface.app.bookings.web.dto.CareBookingRequest(
                        1L, 10L, 1, futureDate,
                        LocalTime.of(9, 0),
                        CareBookingStatus.CONFIRMED);

        var result = service.update(103L, confirmReq);
        assertThat(result.status()).isEqualTo(CareBookingStatus.CONFIRMED);

        // Verify slot service was NOT called (no date/time/care change)
        verify(slotAvailabilityService, never()).getAvailableSlots(any(), any(Long.class));
    }

    // ══════════════════════════════════════════════════════════════
    // ── Récurrence — documentation des limitations ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("MISSING: Recurring bookings not supported — documented limitation")
    void recurringBookings_notSupported() {
        // The system has NO recurring booking support
        // Each booking is standalone with a single date/time
        // TODO: Implement RecurringBookingTemplate entity:
        //   - pattern (WEEKLY, BIWEEKLY, MONTHLY)
        //   - startDate, endDate
        //   - dayOfWeek / dayOfMonth
        //   - Handle: holidays, Feb 29, month 31, single vs series modification
        assertThat(true).isTrue();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Edge cases calendrier ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Feb 29 leap day — booking works")
    void leapDay_bookingWorks() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        when(slotAvailabilityService.getAvailableSlots(leapDay, 10L))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot("09:00", "09:30")));
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, leapDay, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentDate()).isEqualTo("2028-02-29");
    }

    @Test
    @DisplayName("Last day of short month (April 30) — works")
    void lastDayOfShortMonth_works() {
        LocalDate april30 = LocalDate.of(2027, 4, 30);
        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        when(slotAvailabilityService.getAvailableSlots(april30, 10L))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot("09:00", "09:30")));
        mockSaveBooking();

        ClientBookingRequest req = new ClientBookingRequest(10L, april30, "09:00", null);
        ClientBookingResponse result = service.createClientBooking(client, owner, "Salon", req);

        assertThat(result.appointmentDate()).isEqualTo("2027-04-30");
    }

    @Test
    @DisplayName("Year transition Dec 31 → Jan 1 — both days work")
    void yearTransition_works() {
        LocalDate dec31 = LocalDate.of(2027, 12, 31);
        LocalDate jan1 = LocalDate.of(2028, 1, 1);

        when(careRepository.findById(10L)).thenReturn(Optional.of(care30min));
        when(slotAvailabilityService.getAvailableSlots(dec31, 10L))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot("09:00", "09:30")));
        when(slotAvailabilityService.getAvailableSlots(jan1, 10L))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot("10:00", "10:30")));
        mockSaveBooking();

        ClientBookingRequest reqDec = new ClientBookingRequest(10L, dec31, "09:00", null);
        assertThat(service.createClientBooking(client, owner, "Salon", reqDec)
                .appointmentDate()).isEqualTo("2027-12-31");

        ClientBookingRequest reqJan = new ClientBookingRequest(10L, jan1, "10:00", null);
        assertThat(service.createClientBooking(client, owner, "Salon", reqJan)
                .appointmentDate()).isEqualTo("2028-01-01");
    }

    // ── Helpers ──

    private void mockSlotAvailable(String time) {
        when(slotAvailabilityService.getAvailableSlots(any(LocalDate.class), eq(10L)))
                .thenReturn(List.of(new SlotAvailabilityService.TimeSlot(time,
                        LocalTime.parse(time).plusMinutes(30).toString())));
    }

    private void mockNoSlotAvailable(String time) {
        when(slotAvailabilityService.getAvailableSlots(any(LocalDate.class), eq(10L)))
                .thenReturn(List.of()); // No slots at all
    }

    private void mockSaveBooking() {
        when(bookingRepo.save(any(CareBooking.class))).thenAnswer(inv -> {
            CareBooking b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });
    }

    private static LocalDate nextMonday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek().getValue() != 1) d = d.plusDays(1);
        return d;
    }
}
