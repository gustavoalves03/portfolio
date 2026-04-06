package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.HolidayException;
import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.HolidayExceptionRepository;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.availability.repo.BlockedSlotRepository;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.employee.app.LeaveRequestService;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration tests verifying the FULL holiday pipeline:
 * HolidayService (real Jollyday) → HolidayAvailabilityService → SlotAvailabilityService.
 *
 * Repos are mocked; HolidayService uses the real Jollyday library.
 */
@ExtendWith(MockitoExtension.class)
class HolidayIntegrationTests {

    // ── Real services ──
    private final HolidayService realHolidayService = new HolidayService();

    // ── Mocked repos ──
    @Mock private HolidayExceptionRepository exceptionRepo;
    @Mock private OpeningHourRepository openingHourRepo;
    @Mock private BlockedSlotRepository blockedSlotRepo;
    @Mock private CareBookingRepository bookingRepo;
    @Mock private CareRepository careRepo;
    @Mock private LeaveRequestService leaveRequestService;
    @Mock private TenantRepository tenantRepository;

    // ── Services under test ──
    private HolidayAvailabilityService holidayAvailabilityService;
    private SlotAvailabilityService slotAvailabilityService;

    // ── Test fixtures ──
    private Care care60min;
    private Tenant frTenant;
    private Tenant frTenantNotClosed;
    private Tenant luTenant;
    private Tenant noCountryTenant;

    private static final String SLUG_FR = "salon-paris";
    private static final String SLUG_FR_NOT_CLOSED = "salon-paris-open";
    private static final String SLUG_LU = "salon-luxembourg";
    private static final String SLUG_NO_COUNTRY = "salon-no-country";

    @BeforeEach
    void setUp() {
        // Wire real HolidayService into HolidayAvailabilityService
        holidayAvailabilityService = new HolidayAvailabilityService(realHolidayService, exceptionRepo);

        // Wire everything into SlotAvailabilityService
        slotAvailabilityService = new SlotAvailabilityService(
                openingHourRepo, blockedSlotRepo, bookingRepo, careRepo,
                leaveRequestService, holidayAvailabilityService, tenantRepository);

        // Shared care
        care60min = new Care();
        care60min.setId(1L);
        care60min.setName("Soin visage");
        care60min.setDuration(60);

        // French tenant (closedOnHolidays=true)
        frTenant = Tenant.builder()
                .id(1L).slug(SLUG_FR).name("Salon Paris").ownerId(1L)
                .addressCountry("FR").closedOnHolidays(true).bufferMinutes(0)
                .build();

        // French tenant (closedOnHolidays=false)
        frTenantNotClosed = Tenant.builder()
                .id(2L).slug(SLUG_FR_NOT_CLOSED).name("Salon Paris Open").ownerId(2L)
                .addressCountry("FR").closedOnHolidays(false).bufferMinutes(0)
                .build();

        // Luxembourg tenant
        luTenant = Tenant.builder()
                .id(3L).slug(SLUG_LU).name("Salon Luxembourg").ownerId(3L)
                .addressCountry("LU").closedOnHolidays(true).bufferMinutes(0)
                .build();

        // Tenant without country
        noCountryTenant = Tenant.builder()
                .id(4L).slug(SLUG_NO_COUNTRY).name("Salon No Country").ownerId(4L)
                .addressCountry(null).closedOnHolidays(true).bufferMinutes(0)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Helpers ──
    // ══════════════════════════════════════════════════════════════

    private void setTenantContext(String slug, Tenant tenant) {
        TenantContext.setCurrentTenant(slug);
        lenient().when(tenantRepository.findBySlug(slug)).thenReturn(Optional.of(tenant));
    }

    private OpeningHour openingHour(int dow, String open, String close) {
        OpeningHour oh = new OpeningHour();
        oh.setDayOfWeek(dow);
        oh.setOpenTime(LocalTime.parse(open));
        oh.setCloseTime(LocalTime.parse(close));
        return oh;
    }

    /** Set up standard Mon-Sat opening hours (09:00-18:00). */
    private void setupStandardOpeningHours() {
        List<OpeningHour> hours = List.of(
                openingHour(1, "09:00", "18:00"),
                openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"),
                openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"),
                openingHour(6, "09:00", "18:00")
        );
        lenient().when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(hours);
        lenient().when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(hours);
    }

    private void setupCare() {
        lenient().when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
    }

    private void setupNoBlockedSlots(LocalDate date) {
        lenient().when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
    }

    private void setupNoBookings(LocalDate date) {
        lenient().when(bookingRepo.findByAppointmentDateAndStatusNot(eq(date), any()))
                .thenReturn(List.of());
    }

    private void setupNoExceptions() {
        lenient().when(exceptionRepo.findByHolidayDate(any())).thenReturn(Optional.empty());
    }

    // ══════════════════════════════════════════════════════════════
    // ── 1. May 1st (Fete du Travail) in France → salon closed, 0 slots ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. May 1st (Fete du Travail) FR → closed, 0 slots")
    void may1st_france_closed_zeroSlots() {
        // May 1, 2026 is a Friday
        LocalDate may1 = LocalDate.of(2026, 5, 1);

        // Verify Jollyday recognizes it
        assertThat(realHolidayService.isPublicHoliday(may1, "FR")).isTrue();

        // Integration: slot availability should return empty
        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(may1);
        setupNoBookings(may1);
        setupNoExceptions();

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(may1, 1L);
        assertThat(slots).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 2. May 1st with exception open=true → salon open, normal slots ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2. May 1st with exception open=true → salon open, normal slots")
    void may1st_withExceptionOpen_hasSlots() {
        LocalDate may1 = LocalDate.of(2026, 5, 1);

        HolidayException openException = new HolidayException();
        openException.setHolidayDate(may1);
        openException.setOpen(true);
        when(exceptionRepo.findByHolidayDate(may1)).thenReturn(Optional.of(openException));

        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(may1);
        setupNoBookings(may1);

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(may1, 1L);
        assertThat(slots).isNotEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 3. closedOnHolidays=false → May 1st treated as normal day ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("3. closedOnHolidays=false → May 1st treated as normal day")
    void closedOnHolidaysFalse_may1st_normalDay() {
        LocalDate may1 = LocalDate.of(2026, 5, 1);

        setTenantContext(SLUG_FR_NOT_CLOSED, frTenantNotClosed);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(may1);
        setupNoBookings(may1);

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(may1, 1L);
        // May 1, 2026 is a Friday (dow=5), salon is open Mon-Sat
        assertThat(slots).isNotEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 4. December 25 (Christmas) France → closed ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("4. Christmas Dec 25 FR → closed, 0 slots")
    void christmas_france_closed() {
        LocalDate christmas = LocalDate.of(2026, 12, 25);

        assertThat(realHolidayService.isPublicHoliday(christmas, "FR")).isTrue();

        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(christmas);
        setupNoBookings(christmas);
        setupNoExceptions();

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(christmas, 1L);
        assertThat(slots).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 5. June 23 (Luxembourg National Day) for LU tenant → closed ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5. Luxembourg National Day Jun 23 LU → closed")
    void luxembourgNationalDay_lu_closed() {
        LocalDate june23 = LocalDate.of(2026, 6, 23);

        assertThat(realHolidayService.isPublicHoliday(june23, "LU")).isTrue();

        setTenantContext(SLUG_LU, luTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(june23);
        setupNoBookings(june23);
        setupNoExceptions();

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(june23, 1L);
        assertThat(slots).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 6. June 23 for FR tenant → NOT a holiday, open ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6. June 23 for FR tenant → not a holiday, salon open")
    void june23_france_notHoliday_open() {
        LocalDate june23 = LocalDate.of(2026, 6, 23);

        assertThat(realHolidayService.isPublicHoliday(june23, "FR")).isFalse();

        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(june23);
        setupNoBookings(june23);

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(june23, 1L);
        // June 23, 2026 is a Tuesday (dow=2), salon is open
        assertThat(slots).isNotEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 7. Holiday on a Sunday (already closed) → no crash, still empty ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("7. Holiday on a Sunday (already closed) → no crash, still empty")
    void holidayOnSunday_alreadyClosed_noCrash() {
        // Dec 25, 2022 was a Sunday — use it for the test
        // We need a future date, so use a known Sunday that is also a holiday.
        // Nov 1, 2026 is a Sunday (All Saints' Day / Toussaint in France)
        LocalDate nov1 = LocalDate.of(2026, 11, 1);
        // Verify it's a Sunday
        assertThat(nov1.getDayOfWeek().getValue()).isEqualTo(7); // Sunday

        assertThat(realHolidayService.isPublicHoliday(nov1, "FR")).isTrue();

        setTenantContext(SLUG_FR, frTenant);
        // Standard hours: Mon-Sat only (no Sunday)
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(nov1);
        setupNoBookings(nov1);
        setupNoExceptions();

        // Both "closed day" and "holiday" converge → empty, no crash
        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(nov1, 1L);
        assertThat(slots).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 8. Holiday on a Saturday (salon open Saturdays) → closed due to holiday ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("8. Holiday on a Saturday (salon open Sat) → closed due to holiday")
    void holidayOnSaturday_salonOpenSaturdays_closedDueToHoliday() {
        // May 1, 2027 is a Saturday
        LocalDate may1_2027 = LocalDate.of(2027, 5, 1);
        assertThat(may1_2027.getDayOfWeek().getValue()).isEqualTo(6); // Saturday

        assertThat(realHolidayService.isPublicHoliday(may1_2027, "FR")).isTrue();

        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours(); // includes Saturday
        setupCare();
        setupNoBlockedSlots(may1_2027);
        setupNoBookings(may1_2027);
        setupNoExceptions();

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(may1_2027, 1L);
        assertThat(slots).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 9. Non-holiday date → salon open as normal ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("9. Non-holiday date → salon open, has normal slots")
    void nonHolidayDate_salonOpen_normalSlots() {
        // June 15, 2026 is a Monday (not a holiday)
        LocalDate june15 = LocalDate.of(2026, 6, 15);

        assertThat(realHolidayService.isPublicHoliday(june15, "FR")).isFalse();

        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(june15);
        setupNoBookings(june15);

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(june15, 1L);
        assertThat(slots).isNotEmpty();
        // With 09:00-18:00 and 60min care, slots: 09:00, 09:30, ..., 17:00
        assertThat(slots.get(0).startTime()).isEqualTo("09:00");
    }

    // ══════════════════════════════════════════════════════════════
    // ── 10. Employee availability on a holiday → also 0 slots ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("10. Employee availability on a holiday → also 0 slots")
    void employeeAvailability_holiday_zeroSlots() {
        LocalDate may1 = LocalDate.of(2026, 5, 1);
        Long employeeId = 42L;

        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(may1);
        setupNoExceptions();
        lenient().when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(eq(may1), eq(employeeId), any()))
                .thenReturn(List.of());
        lenient().when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(employeeId))
                .thenReturn(List.of());
        lenient().when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(employeeId), any()))
                .thenReturn(List.of());
        lenient().when(leaveRequestService.isOnLeave(employeeId, may1)).thenReturn(false);

        List<SlotAvailabilityService.TimeSlot> slots =
                slotAvailabilityService.getAvailableSlots(may1, 1L, employeeId);
        assertThat(slots).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 11. Holiday with no country set on tenant → not closed (graceful) ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("11. Holiday with no country on tenant → not closed, normal slots")
    void noCountryOnTenant_notClosed_normalSlots() {
        LocalDate may1 = LocalDate.of(2026, 5, 1);

        setTenantContext(SLUG_NO_COUNTRY, noCountryTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoBlockedSlots(may1);
        setupNoBookings(may1);

        List<SlotAvailabilityService.TimeSlot> slots = slotAvailabilityService.getAvailableSlots(may1, 1L);
        // No country → holiday check is skipped → slots available
        assertThat(slots).isNotEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── 12. Multiple holidays in a month → all blocked ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("12. Multiple holidays in a month → all blocked")
    void multipleHolidaysInMonth_allBlocked() {
        // May 2026: May 1 (Fete du Travail), May 8 (Victoire 1945), May 14 (Ascension)
        LocalDate may1 = LocalDate.of(2026, 5, 1);
        LocalDate may8 = LocalDate.of(2026, 5, 8); // Victoire 1945

        assertThat(realHolidayService.isPublicHoliday(may1, "FR")).isTrue();
        assertThat(realHolidayService.isPublicHoliday(may8, "FR")).isTrue();

        // Both should produce 0 slots
        setTenantContext(SLUG_FR, frTenant);
        setupStandardOpeningHours();
        setupCare();
        setupNoExceptions();

        // May 1
        setupNoBlockedSlots(may1);
        setupNoBookings(may1);
        List<SlotAvailabilityService.TimeSlot> slotsMay1 = slotAvailabilityService.getAvailableSlots(may1, 1L);
        assertThat(slotsMay1).isEmpty();

        // May 8
        setupNoBlockedSlots(may8);
        setupNoBookings(may8);
        List<SlotAvailabilityService.TimeSlot> slotsMay8 = slotAvailabilityService.getAvailableSlots(may8, 1L);
        assertThat(slotsMay8).isEmpty();

        // A normal day in May (May 4, Monday) should still have slots
        LocalDate may4 = LocalDate.of(2026, 5, 4);
        assertThat(realHolidayService.isPublicHoliday(may4, "FR")).isFalse();
        setupNoBlockedSlots(may4);
        setupNoBookings(may4);
        List<SlotAvailabilityService.TimeSlot> slotsMay4 = slotAvailabilityService.getAvailableSlots(may4, 1L);
        assertThat(slotsMay4).isNotEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Additional: Jollyday direct verification ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Jollyday: verify known French public holidays")
    void jollyday_verifyFrenchHolidays() {
        // Standard French public holidays in 2026
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 1, 1), "FR")).isTrue();   // New Year
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 5, 1), "FR")).isTrue();   // Fete du Travail
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 5, 8), "FR")).isTrue();   // Victoire 1945
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 7, 14), "FR")).isTrue();  // Fete Nationale
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 8, 15), "FR")).isTrue();  // Assumption
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 11, 1), "FR")).isTrue();  // Toussaint
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 11, 11), "FR")).isTrue(); // Armistice
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 12, 25), "FR")).isTrue(); // Noel
    }

    @Test
    @DisplayName("Jollyday: verify non-holiday dates in France")
    void jollyday_verifyNonHolidays() {
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 3, 15), "FR")).isFalse();
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 6, 15), "FR")).isFalse();
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 9, 1), "FR")).isFalse();
    }

    @Test
    @DisplayName("Jollyday: Luxembourg National Day recognized, not recognized in FR")
    void jollyday_luxembourgNationalDay_crossCountry() {
        LocalDate june23 = LocalDate.of(2026, 6, 23);
        assertThat(realHolidayService.isPublicHoliday(june23, "LU")).isTrue();
        assertThat(realHolidayService.isPublicHoliday(june23, "FR")).isFalse();
    }

    @Test
    @DisplayName("HolidayService: null/blank country → graceful false")
    void holidayService_nullOrBlankCountry_gracefulFalse() {
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 5, 1), null)).isFalse();
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 5, 1), "")).isFalse();
        assertThat(realHolidayService.isPublicHoliday(LocalDate.of(2026, 5, 1), "  ")).isFalse();
    }
}
