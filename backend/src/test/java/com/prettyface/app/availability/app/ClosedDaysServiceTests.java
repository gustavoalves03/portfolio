package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.BlockedSlot;
import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.BlockedSlotRepository;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClosedDaysServiceTests {

    @Mock
    private OpeningHourRepository openingHourRepo;

    @Mock
    private BlockedSlotRepository blockedSlotRepo;

    @Mock
    private HolidayAvailabilityService holidayAvailabilityService;

    @Mock
    private TenantRepository tenantRepository;

    private ClosedDaysService service;

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final String SLUG = "test-salon";

    private void initService(LocalDate today, LocalTime now) {
        Instant fixed = ZonedDateTime.of(today, now, ZONE).toInstant();
        Clock clock = Clock.fixed(fixed, ZONE);
        service = new ClosedDaysService(openingHourRepo, blockedSlotRepo,
                holidayAvailabilityService, tenantRepository, clock);
    }

    private OpeningHour openingHour(int dow, String open, String close) {
        OpeningHour oh = new OpeningHour();
        oh.setDayOfWeek(dow);
        oh.setOpenTime(LocalTime.parse(open));
        oh.setCloseTime(LocalTime.parse(close));
        return oh;
    }

    private BlockedSlot fullDayBlock(LocalDate date) {
        BlockedSlot bs = new BlockedSlot();
        bs.setDate(date);
        bs.setFullDay(true);
        return bs;
    }

    private BlockedSlot partialBlock(LocalDate date, String start, String end) {
        BlockedSlot bs = new BlockedSlot();
        bs.setDate(date);
        bs.setFullDay(false);
        bs.setStartTime(LocalTime.parse(start));
        bs.setEndTime(LocalTime.parse(end));
        return bs;
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // Range edges
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("null from/to → empty set")
    void nullArgs_returnsEmpty() {
        initService(LocalDate.of(2026, 5, 5), LocalTime.NOON);
        assertThat(service.getClosedDays(null, null)).isEmpty();
        assertThat(service.getClosedDays(LocalDate.of(2026, 5, 1), null)).isEmpty();
        assertThat(service.getClosedDays(null, LocalDate.of(2026, 5, 1))).isEmpty();
    }

    @Test
    @DisplayName("to before from → empty set")
    void inverseRange_returnsEmpty() {
        initService(LocalDate.of(2026, 5, 5), LocalTime.NOON);
        Set<LocalDate> result = service.getClosedDays(
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1));
        assertThat(result).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // Past dates
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Past dates within range are always closed")
    void pastDates_alwaysClosed() {
        // Today = May 5; salon open every day 9-18
        initService(LocalDate.of(2026, 5, 5), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00"),
                openingHour(7, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7));

        assertThat(closed).contains(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2),
                LocalDate.of(2026, 5, 3),
                LocalDate.of(2026, 5, 4)
        );
        assertThat(closed).doesNotContain(LocalDate.of(2026, 5, 5)); // today, before close time
    }

    // ══════════════════════════════════════════════════════════════
    // Weekly closed days
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Days with no opening hours (sunday) are closed")
    void weeklyClosed_sunday() {
        // Today = Monday May 4 2026, salon open Mon-Sat
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 10));

        // May 10 2026 is a Sunday → closed
        assertThat(closed).contains(LocalDate.of(2026, 5, 10));
        // Other days remain open
        assertThat(closed).doesNotContain(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
                LocalDate.of(2026, 5, 6), LocalDate.of(2026, 5, 7),
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 9)
        );
    }

    // ══════════════════════════════════════════════════════════════
    // Holidays
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Public holiday with closedOnHolidays=true → closed")
    void holiday_closed() {
        TenantContext.setCurrentTenant(SLUG);
        Tenant tenant = new Tenant();
        tenant.setSlug(SLUG);
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(true);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00"),
                openingHour(7, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(0).equals(mayFirst));

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 5));

        assertThat(closed).contains(mayFirst);
    }

    @Test
    @DisplayName("Holiday exception (open) → not closed")
    void holidayException_overridesToOpen() {
        TenantContext.setCurrentTenant(SLUG);
        Tenant tenant = new Tenant();
        tenant.setSlug(SLUG);
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(true);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00"),
                openingHour(7, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        // HolidayAvailabilityService already factors the exception in: returns false everywhere.
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenReturn(false);

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 5));

        assertThat(closed).doesNotContain(mayFirst);
    }

    @Test
    @DisplayName("Holiday with closedOnHolidays=false → not closed (feature off)")
    void holidayDisabled_notClosed() {
        TenantContext.setCurrentTenant(SLUG);
        Tenant tenant = new Tenant();
        tenant.setSlug(SLUG);
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(false);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(5, "09:00", "18:00") // Friday only
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        LocalDate mayFirst = LocalDate.of(2026, 5, 1); // Friday in 2026
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenReturn(false);

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 5));

        assertThat(closed).doesNotContain(mayFirst);
    }

    // ══════════════════════════════════════════════════════════════
    // Full-day blocks
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Full-day block → closed")
    void fullDayBlock_closed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00"),
                openingHour(7, "09:00", "18:00")
        ));
        LocalDate blockedDate = LocalDate.of(2026, 5, 6);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(fullDayBlock(blockedDate)));

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 10));

        assertThat(closed).contains(blockedDate);
    }

    @Test
    @DisplayName("Partial-time block → NOT closed (only slots reduced)")
    void partialBlock_notClosed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00"),
                openingHour(7, "09:00", "18:00")
        ));
        LocalDate partiallyBlockedDate = LocalDate.of(2026, 5, 6);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(partialBlock(partiallyBlockedDate, "09:00", "12:00")));

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 10));

        assertThat(closed).doesNotContain(partiallyBlockedDate);
    }

    // ══════════════════════════════════════════════════════════════
    // Today exhausted (Option B)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Today before closeTime → not closed")
    void today_beforeCloseTime_open() {
        // Today = Monday May 4 17:30, salon closes at 18:00
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(17, 30));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        assertThat(closed).doesNotContain(LocalDate.of(2026, 5, 4));
    }

    @Test
    @DisplayName("Today exactly at closeTime → closed")
    void today_atCloseTime_closed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(18, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        assertThat(closed).contains(LocalDate.of(2026, 5, 4));
    }

    @Test
    @DisplayName("Today after closeTime → closed")
    void today_afterCloseTime_closed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(19, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        assertThat(closed).contains(LocalDate.of(2026, 5, 4));
    }

    @Test
    @DisplayName("Today with split shifts uses the latest closeTime")
    void today_splitShifts_usesLatestClose() {
        // Mon: 09-12 then 14-19 → latest close = 19:00
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(13, 0)); // lunch break
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "12:00"),
                openingHour(1, "14:00", "19:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        // 13:00 < 19:00 → still open today (the user can book the 14-19 window)
        assertThat(closed).doesNotContain(LocalDate.of(2026, 5, 4));
    }

    // ══════════════════════════════════════════════════════════════
    // Combined cases
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Multiple closure reasons combined in one range")
    void combinedReasons() {
        TenantContext.setCurrentTenant(SLUG);
        Tenant tenant = new Tenant();
        tenant.setSlug(SLUG);
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(true);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        // Mon-Fri only
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00")
        ));
        // Block Wednesday May 6
        LocalDate blockedDate = LocalDate.of(2026, 5, 6);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(fullDayBlock(blockedDate)));
        // 1er mai férié
        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        lenient().when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(0).equals(mayFirst));

        Set<LocalDate> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 10));

        assertThat(closed).contains(
                mayFirst,                          // holiday (Friday)
                LocalDate.of(2026, 5, 2),          // Saturday — weekly closed
                LocalDate.of(2026, 5, 3),          // Sunday — weekly closed
                blockedDate,                       // full-day block
                LocalDate.of(2026, 5, 9),          // Saturday
                LocalDate.of(2026, 5, 10)          // Sunday
        );
        assertThat(closed).doesNotContain(
                LocalDate.of(2026, 4, 28),         // Tuesday today, open before close
                LocalDate.of(2026, 4, 29),         // Wednesday
                LocalDate.of(2026, 4, 30),         // Thursday
                LocalDate.of(2026, 5, 4),          // Monday
                LocalDate.of(2026, 5, 5),          // Tuesday
                LocalDate.of(2026, 5, 7),          // Thursday
                LocalDate.of(2026, 5, 8)           // Friday
        );
    }
}
