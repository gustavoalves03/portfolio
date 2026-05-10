package com.luxpretty.app.bookings.app;

import com.luxpretty.app.availability.domain.OpeningHour;
import com.luxpretty.app.availability.repo.OpeningHourRepository;
import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.bookings.web.dto.AnalyticsResponse;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests focused on the KPI math (revenue, attendance rate, avg basket,
 * status breakdown, trends) — the dashboard's source of truth. Period range
 * resolution is left to the production code; tests stub the repository to
 * return whatever fixture they need.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTests {

    @Mock private CareBookingRepository bookingRepo;
    @Mock private OpeningHourRepository openingHourRepo;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(bookingRepo, openingHourRepo);
        // Default: no opening hours → occupancy stays 0 unless a test sets some.
        lenient().when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of());
    }

    private Care care(long id, int priceCents, int durationMin) {
        Care c = new Care();
        c.setId(id);
        c.setName("Soin " + id);
        c.setPrice(priceCents);
        c.setDuration(durationMin);
        return c;
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setEmail("u" + id + "@e.st");
        return u;
    }

    private CareBooking booking(long id, CareBookingStatus status, Care c, int quantity, long userId) {
        CareBooking b = new CareBooking();
        b.setId(id);
        b.setStatus(status);
        b.setCare(c);
        b.setQuantity(quantity);
        b.setUser(user(userId));
        b.setAppointmentDate(LocalDate.now());
        b.setAppointmentTime(LocalTime.of(10, 0));
        return b;
    }

    private void mockBookings(List<CareBooking> currentPeriod, List<CareBooking> previousPeriod) {
        // The service issues two queries (current & previous range). We can't
        // distinguish the calls from the matcher alone, so we sequence the
        // returns: first call → current, second → previous.
        when(bookingRepo.findByAppointmentDateBetween(any(), any()))
                .thenReturn(new ArrayList<>(currentPeriod))
                .thenReturn(new ArrayList<>(previousPeriod));
    }

    private AnalyticsResponse runWeek() {
        return service.compute("week", null, null);
    }

    // ══════════════════════════════════════════════════════════════
    // Empty data
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Empty period: every numeric KPI is 0, attendance/occupancy 0.0")
    void emptyPeriod_zeros() {
        mockBookings(List.of(), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.totalBookings()).isZero();
        assertThat(r.totalRevenue()).isZero();
        assertThat(r.cancelledCount()).isZero();
        assertThat(r.noShowCount()).isZero();
        assertThat(r.attendanceRate()).isZero();
        assertThat(r.avgBasket()).isZero();
        assertThat(r.newClientsCount()).isZero();
        assertThat(r.recurringClientsCount()).isZero();
    }

    // ══════════════════════════════════════════════════════════════
    // Revenue: excludes CANCELLED + NO_SHOW, multiplies by quantity
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Revenue sums confirmed/pending and ignores cancelled/no-show")
    void revenue_excludesCancelledAndNoShow() {
        Care c = care(1, 5000, 60); // 50 €
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1),  // 50
                booking(2, CareBookingStatus.PENDING,   c, 1, 2),  // 50
                booking(3, CareBookingStatus.CANCELLED, c, 1, 3),  //  0
                booking(4, CareBookingStatus.NO_SHOW,   c, 1, 4)   //  0
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.totalRevenue()).isEqualTo(100_00); // 100 € in cents
    }

    @Test
    @DisplayName("Revenue multiplies care.price by quantity")
    void revenue_multipliesByQuantity() {
        Care c = care(1, 4000, 30); // 40 €
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 3, 1),  // 40 × 3 = 120
                booking(2, CareBookingStatus.CONFIRMED, c, 2, 2)   // 40 × 2 =  80
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.totalRevenue()).isEqualTo(200_00);
    }

    @Test
    @DisplayName("Revenue is zero when every booking is cancelled or no-show")
    void revenue_zero_whenAllCancelledOrNoShow() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CANCELLED, c, 1, 1),
                booking(2, CareBookingStatus.NO_SHOW,   c, 1, 2)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.totalRevenue()).isZero();
    }

    // ══════════════════════════════════════════════════════════════
    // Status counts
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Status counts: cancelled, no-show — totalBookings counts everything")
    void statusCounts_consistent() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1),
                booking(2, CareBookingStatus.CONFIRMED, c, 1, 2),
                booking(3, CareBookingStatus.PENDING,   c, 1, 3),
                booking(4, CareBookingStatus.CANCELLED, c, 1, 4),
                booking(5, CareBookingStatus.CANCELLED, c, 1, 5),
                booking(6, CareBookingStatus.NO_SHOW,   c, 1, 6)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.totalBookings()).isEqualTo(6);
        assertThat(r.cancelledCount()).isEqualTo(2);
        assertThat(r.noShowCount()).isEqualTo(1);
    }

    // ══════════════════════════════════════════════════════════════
    // Attendance rate = (CONFIRMED + PENDING) / totalBookings
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Attendance rate = (confirmed + pending) / total")
    void attendanceRate_basic() {
        Care c = care(1, 5000, 60);
        // 4/5 attended → 0.8
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1),
                booking(2, CareBookingStatus.CONFIRMED, c, 1, 2),
                booking(3, CareBookingStatus.CONFIRMED, c, 1, 3),
                booking(4, CareBookingStatus.PENDING,   c, 1, 4),
                booking(5, CareBookingStatus.CANCELLED, c, 1, 5)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.attendanceRate()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("Attendance rate is 0.0 when no bookings (no division by zero)")
    void attendanceRate_emptyDoesNotDivByZero() {
        mockBookings(List.of(), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.attendanceRate()).isZero();
    }

    @Test
    @DisplayName("Attendance rate is 0.0 when every booking was cancelled / no-show")
    void attendanceRate_allCancelled() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CANCELLED, c, 1, 1),
                booking(2, CareBookingStatus.NO_SHOW,   c, 1, 2)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.attendanceRate()).isZero();
    }

    // ══════════════════════════════════════════════════════════════
    // Average basket: revenue / attendedCount
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("avgBasket = totalRevenue / attended count, integer division")
    void avgBasket_basic() {
        Care c50 = care(1, 5000, 60); //  50 €
        Care c80 = care(2, 8000, 60); //  80 €
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c50, 1, 1),
                booking(2, CareBookingStatus.CONFIRMED, c80, 1, 2)
        ), List.of());

        AnalyticsResponse r = runWeek();

        // 50 + 80 = 130, divided by 2 = 65 €
        assertThat(r.totalRevenue()).isEqualTo(130_00);
        assertThat(r.avgBasket()).isEqualTo(65_00);
    }

    @Test
    @DisplayName("avgBasket is 0 when no attended booking (avoids div-by-zero)")
    void avgBasket_zero_whenNoAttended() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CANCELLED, c, 1, 1),
                booking(2, CareBookingStatus.NO_SHOW,   c, 1, 2)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.avgBasket()).isZero();
    }

    // ══════════════════════════════════════════════════════════════
    // Trends (current vs previous period)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Bookings trend: +50% when current=6, previous=4")
    void bookingsTrend_positive() {
        Care c = care(1, 5000, 60);
        List<CareBooking> current = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            current.add(booking(i + 1, CareBookingStatus.CONFIRMED, c, 1, i + 1));
        }
        List<CareBooking> previous = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            previous.add(booking(100 + i, CareBookingStatus.CONFIRMED, c, 1, i + 1));
        }
        mockBookings(current, previous);

        AnalyticsResponse r = runWeek();

        // (6 - 4) / 4 * 100 = 50.0
        assertThat(r.bookingsTrend()).isNotNull();
        assertThat(r.bookingsTrend()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Trends report 100% when previous=0 and current>0 (full growth)")
    void trend_100_whenPreviousEmpty_andCurrentNonZero() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.bookingsTrend()).isEqualTo(100.0);
        assertThat(r.revenueTrend()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Trends are null when both periods are empty (no signal)")
    void trend_null_whenBothEmpty() {
        mockBookings(List.of(), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.bookingsTrend()).isNull();
        assertThat(r.revenueTrend()).isNull();
    }

    // ══════════════════════════════════════════════════════════════
    // Status breakdown
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Status breakdown returns counts per status")
    void statusBreakdown_groupsCorrectly() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1),
                booking(2, CareBookingStatus.CONFIRMED, c, 1, 2),
                booking(3, CareBookingStatus.PENDING,   c, 1, 3),
                booking(4, CareBookingStatus.CANCELLED, c, 1, 4),
                booking(5, CareBookingStatus.NO_SHOW,   c, 1, 5)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.statusBreakdown())
                .containsEntry("CONFIRMED", 2)
                .containsEntry("PENDING", 1)
                .containsEntry("CANCELLED", 1)
                .containsEntry("NO_SHOW", 1);
    }

    // ══════════════════════════════════════════════════════════════
    // New vs recurring clients
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Clients are recurring iff seen in previous period, new otherwise")
    void newVsRecurring_basic() {
        Care c = care(1, 5000, 60);
        // current: users 1, 2, 3
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1),
                booking(2, CareBookingStatus.CONFIRMED, c, 1, 2),
                booking(3, CareBookingStatus.CONFIRMED, c, 1, 3)
        ), List.of(
                // previous: only user 1 was here before → recurring
                booking(101, CareBookingStatus.CONFIRMED, c, 1, 1)
        ));

        AnalyticsResponse r = runWeek();

        assertThat(r.recurringClientsCount()).isEqualTo(1);
        assertThat(r.newClientsCount()).isEqualTo(2);
    }

    // ══════════════════════════════════════════════════════════════
    // Care filter
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("careId filter scopes revenue/totals to a single care")
    void careFilter_scopesData() {
        Care c1 = care(1, 5000, 60);
        Care c2 = care(2, 9000, 60);
        when(bookingRepo.findByAppointmentDateBetween(any(), any()))
                .thenReturn(new ArrayList<>(List.of(
                        booking(1, CareBookingStatus.CONFIRMED, c1, 1, 1),
                        booking(2, CareBookingStatus.CONFIRMED, c1, 1, 2),
                        booking(3, CareBookingStatus.CONFIRMED, c2, 1, 3)
                )))
                .thenReturn(new ArrayList<>(List.of()));

        AnalyticsResponse r = service.compute("week", null, 1L);

        // Only the two c1 bookings count → 2 × 50 = 100
        assertThat(r.totalBookings()).isEqualTo(2);
        assertThat(r.totalRevenue()).isEqualTo(100_00);
    }

    // ══════════════════════════════════════════════════════════════
    // Occupancy rate
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Occupancy is 0 when there are no opening hours configured")
    void occupancy_zero_whenNoHours() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1)
        ), List.of());
        // setUp already returns empty hours via the right repo method.

        AnalyticsResponse r = runWeek();

        assertThat(r.occupancyRate()).isZero();
    }

    @Test
    @DisplayName("Occupancy ratio: bookings duration / total open minutes in window")
    void occupancy_basicRatio() {
        // Salon open every day 10:00-12:00 (120 min/day → 840 min/week).
        // 2 bookings of 60 min each = 120 min consumed → ~0.1428.
        Care c = care(1, 5000, 60);
        List<OpeningHour> hours = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            OpeningHour h = new OpeningHour();
            h.setDayOfWeek(dow);
            h.setOpenTime(LocalTime.of(10, 0));
            h.setCloseTime(LocalTime.of(12, 0));
            hours.add(h);
        }
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(hours);

        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1),
                booking(2, CareBookingStatus.CONFIRMED, c, 1, 2)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.occupancyRate()).isBetween(0.0, 1.0);
        assertThat(r.occupancyRate()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Occupancy excludes CANCELLED and NO_SHOW from booked minutes")
    void occupancy_excludesCancelledAndNoShow() {
        Care c = care(1, 5000, 60);
        List<OpeningHour> hours = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            OpeningHour h = new OpeningHour();
            h.setDayOfWeek(dow);
            h.setOpenTime(LocalTime.of(10, 0));
            h.setCloseTime(LocalTime.of(12, 0));
            hours.add(h);
        }
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(hours);

        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1), // 60 min
                booking(2, CareBookingStatus.CANCELLED, c, 1, 2), // skipped
                booking(3, CareBookingStatus.NO_SHOW,   c, 1, 3)  // skipped
        ), List.of());

        AnalyticsResponse r1 = runWeek();
        double withCancelled = r1.occupancyRate();

        // Same fixture but only the confirmed one for comparison
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1)
        ), List.of());
        AnalyticsResponse r2 = runWeek();
        double withoutNoise = r2.occupancyRate();

        // Both rates must be equal — cancelled/no-show don't add booked minutes.
        assertThat(withCancelled).isEqualTo(withoutNoise);
    }

    // ══════════════════════════════════════════════════════════════
    // Forecast revenue (placeholder coverage — implementation may evolve)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Forecast revenue is non-negative")
    void forecast_nonNegative() {
        Care c = care(1, 5000, 60);
        mockBookings(List.of(
                booking(1, CareBookingStatus.CONFIRMED, c, 1, 1)
        ), List.of());

        AnalyticsResponse r = runWeek();

        assertThat(r.forecastRevenue()).isGreaterThanOrEqualTo(0);
    }
}
