package com.luxpretty.app.availability.app;

import com.luxpretty.app.availability.app.ClosedDaysService.ClosedDayReason;
import com.luxpretty.app.availability.domain.BlockedSlot;
import com.luxpretty.app.availability.domain.OpeningHour;
import com.luxpretty.app.availability.repo.BlockedSlotRepository;
import com.luxpretty.app.availability.repo.OpeningHourRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private void mockOpenAllWeek() {
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00"),
                openingHour(7, "09:00", "18:00")
        ));
    }

    private void mockTenantWithHolidays(boolean closedOnHolidays) {
        TenantContext.setCurrentTenant(SLUG);
        Tenant tenant = new Tenant();
        tenant.setSlug(SLUG);
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(closedOnHolidays);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // Range edges
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("null from/to → empty map")
    void nullArgs_returnsEmpty() {
        initService(LocalDate.of(2026, 5, 5), LocalTime.NOON);
        assertThat(service.getClosedDays(null, null)).isEmpty();
        assertThat(service.getClosedDays(LocalDate.of(2026, 5, 1), null)).isEmpty();
        assertThat(service.getClosedDays(null, LocalDate.of(2026, 5, 1))).isEmpty();
    }

    @Test
    @DisplayName("to before from → empty map")
    void inverseRange_returnsEmpty() {
        initService(LocalDate.of(2026, 5, 5), LocalTime.NOON);
        Map<LocalDate, ClosedDayReason> result = service.getClosedDays(
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1));
        assertThat(result).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // Past dates → PAST
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Past dates within range are tagged PAST")
    void pastDates_taggedPast() {
        initService(LocalDate.of(2026, 5, 5), LocalTime.of(10, 0));
        mockOpenAllWeek();
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7));

        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 1), ClosedDayReason.PAST);
        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 4), ClosedDayReason.PAST);
        assertThat(closed).doesNotContainKey(LocalDate.of(2026, 5, 5));
    }

    // ══════════════════════════════════════════════════════════════
    // Weekly closed → WEEKLY_CLOSED
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Days with no opening hours are tagged WEEKLY_CLOSED")
    void weeklyClosed_taggedWeeklyClosed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 10));

        // May 10 2026 is a Sunday → WEEKLY_CLOSED
        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 10), ClosedDayReason.WEEKLY_CLOSED);
        assertThat(closed).doesNotContainKeys(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
                LocalDate.of(2026, 5, 6), LocalDate.of(2026, 5, 7),
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 9)
        );
    }

    // ══════════════════════════════════════════════════════════════
    // Holidays → HOLIDAY (with priority over WEEKLY_CLOSED / FULL_DAY_BLOCK)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Public holiday with closedOnHolidays=true → HOLIDAY")
    void holiday_taggedHoliday() {
        mockTenantWithHolidays(true);
        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        mockOpenAllWeek();
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(0).equals(mayFirst));

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 5));

        assertThat(closed).containsEntry(mayFirst, ClosedDayReason.HOLIDAY);
    }

    @Test
    @DisplayName("Holiday exception (open) → not closed")
    void holidayException_overridesToOpen() {
        mockTenantWithHolidays(true);
        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        mockOpenAllWeek();
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenReturn(false);

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 5));

        assertThat(closed).doesNotContainKey(mayFirst);
    }

    @Test
    @DisplayName("Holiday with closedOnHolidays=false → not closed (feature off)")
    void holidayDisabled_notClosed() {
        mockTenantWithHolidays(false);
        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(5, "09:00", "18:00") // Friday only
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        LocalDate mayFirst = LocalDate.of(2026, 5, 1); // Friday
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenReturn(false);

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 5));

        assertThat(closed).doesNotContainKey(mayFirst);
    }

    @Test
    @DisplayName("Holiday on a weekly-closed day → HOLIDAY wins")
    void holiday_onWeeklyClosedDay_holidayWins() {
        mockTenantWithHolidays(true);
        // Salon open Mon-Sat (closed Sunday). May 1 2026 is a Friday.
        // Use a holiday that falls on a Sunday for this test: Easter Sunday April 5, 2026.
        initService(LocalDate.of(2026, 4, 1), LocalTime.of(10, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00"), openingHour(6, "09:00", "18:00")
                // No Sunday
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        LocalDate easterSunday = LocalDate.of(2026, 4, 5);
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(0).equals(easterSunday));

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 12));

        // Reason is HOLIDAY (not WEEKLY_CLOSED) — holiday is more informative
        assertThat(closed).containsEntry(easterSunday, ClosedDayReason.HOLIDAY);
        // Other Sundays in range → WEEKLY_CLOSED
        assertThat(closed).containsEntry(LocalDate.of(2026, 4, 12), ClosedDayReason.WEEKLY_CLOSED);
    }

    @Test
    @DisplayName("Holiday on a full-day block → HOLIDAY wins")
    void holiday_onFullDayBlock_holidayWins() {
        mockTenantWithHolidays(true);
        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        mockOpenAllWeek();

        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(fullDayBlock(mayFirst)));
        when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(0).equals(mayFirst));

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 5));

        assertThat(closed).containsEntry(mayFirst, ClosedDayReason.HOLIDAY);
    }

    // ══════════════════════════════════════════════════════════════
    // Full-day blocks → FULL_DAY_BLOCK
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Full-day block → FULL_DAY_BLOCK")
    void fullDayBlock_taggedFullDayBlock() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(10, 0));
        mockOpenAllWeek();
        LocalDate blockedDate = LocalDate.of(2026, 5, 6);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(fullDayBlock(blockedDate)));

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 10));

        assertThat(closed).containsEntry(blockedDate, ClosedDayReason.FULL_DAY_BLOCK);
    }

    @Test
    @DisplayName("Partial-time block → NOT closed (only slots reduced)")
    void partialBlock_notClosed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(10, 0));
        mockOpenAllWeek();
        LocalDate partiallyBlockedDate = LocalDate.of(2026, 5, 6);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(partialBlock(partiallyBlockedDate, "09:00", "12:00")));

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 10));

        assertThat(closed).doesNotContainKey(partiallyBlockedDate);
    }

    // ══════════════════════════════════════════════════════════════
    // Today exhausted → TODAY_CLOSED (Option B)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Today before closeTime → not closed")
    void today_beforeCloseTime_open() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(17, 30));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        assertThat(closed).doesNotContainKey(LocalDate.of(2026, 5, 4));
    }

    @Test
    @DisplayName("Today exactly at closeTime → TODAY_CLOSED")
    void today_atCloseTime_taggedTodayClosed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(18, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 4), ClosedDayReason.TODAY_CLOSED);
    }

    @Test
    @DisplayName("Today after closeTime → TODAY_CLOSED")
    void today_afterCloseTime_taggedTodayClosed() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(19, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 4), ClosedDayReason.TODAY_CLOSED);
    }

    @Test
    @DisplayName("Today with split shifts uses the latest closeTime")
    void today_splitShifts_usesLatestClose() {
        initService(LocalDate.of(2026, 5, 4), LocalTime.of(13, 0));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "12:00"),
                openingHour(1, "14:00", "19:00")
        ));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 4));

        assertThat(closed).doesNotContainKey(LocalDate.of(2026, 5, 4));
    }

    // ══════════════════════════════════════════════════════════════
    // Combined cases
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Multiple closure reasons combined in one range")
    void combinedReasons() {
        mockTenantWithHolidays(true);
        initService(LocalDate.of(2026, 4, 28), LocalTime.of(10, 0));
        // Mon-Fri only
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of(
                openingHour(1, "09:00", "18:00"), openingHour(2, "09:00", "18:00"),
                openingHour(3, "09:00", "18:00"), openingHour(4, "09:00", "18:00"),
                openingHour(5, "09:00", "18:00")
        ));
        // Block Wednesday May 6 (full day)
        LocalDate blockedDate = LocalDate.of(2026, 5, 6);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(fullDayBlock(blockedDate)));
        // 1er mai férié
        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        lenient().when(holidayAvailabilityService.isClosedForHoliday(any(), anyString(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(0).equals(mayFirst));

        Map<LocalDate, ClosedDayReason> closed = service.getClosedDays(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 10));

        assertThat(closed).containsEntry(mayFirst, ClosedDayReason.HOLIDAY);
        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 2), ClosedDayReason.WEEKLY_CLOSED);
        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 3), ClosedDayReason.WEEKLY_CLOSED);
        assertThat(closed).containsEntry(blockedDate, ClosedDayReason.FULL_DAY_BLOCK);
        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 9), ClosedDayReason.WEEKLY_CLOSED);
        assertThat(closed).containsEntry(LocalDate.of(2026, 5, 10), ClosedDayReason.WEEKLY_CLOSED);
        assertThat(closed).doesNotContainKeys(
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 4, 29), LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
                LocalDate.of(2026, 5, 7), LocalDate.of(2026, 5, 8)
        );
    }
}
