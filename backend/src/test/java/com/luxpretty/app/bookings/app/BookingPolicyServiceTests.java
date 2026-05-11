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
