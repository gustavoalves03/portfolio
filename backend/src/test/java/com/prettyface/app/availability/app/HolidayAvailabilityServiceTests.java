package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.HolidayException;
import com.prettyface.app.availability.repo.HolidayExceptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayAvailabilityServiceTests {

    @Mock
    private HolidayService holidayService;

    @Mock
    private HolidayExceptionRepository exceptionRepo;

    @InjectMocks
    private HolidayAvailabilityService service;

    private static final LocalDate CHRISTMAS = LocalDate.of(2026, 12, 25);
    private static final LocalDate NORMAL_DAY = LocalDate.of(2026, 6, 15); // A Monday, not a holiday

    // ══════════════════════════════════════════════════════════════
    // ── Override: salon decides to open on a holiday ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Holiday + closedOnHolidays=true + no exception → salon closed")
    void holiday_closedOnHolidays_noException_closed() {
        when(holidayService.isPublicHoliday(CHRISTMAS, "FR")).thenReturn(true);
        when(exceptionRepo.findByHolidayDate(CHRISTMAS)).thenReturn(Optional.empty());

        boolean result = service.isClosedForHoliday(CHRISTMAS, "FR", true);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Holiday + closedOnHolidays=true + exception open=true → salon OPEN (override)")
    void holiday_closedOnHolidays_exceptionOpen_salonOpen() {
        when(holidayService.isPublicHoliday(CHRISTMAS, "FR")).thenReturn(true);

        HolidayException exception = new HolidayException();
        exception.setHolidayDate(CHRISTMAS);
        exception.setOpen(true);
        when(exceptionRepo.findByHolidayDate(CHRISTMAS)).thenReturn(Optional.of(exception));

        boolean result = service.isClosedForHoliday(CHRISTMAS, "FR", true);

        // Override: salon decides to open despite holiday
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Holiday + closedOnHolidays=true + exception open=false → salon closed (explicit confirm)")
    void holiday_closedOnHolidays_exceptionClosed_salonClosed() {
        when(holidayService.isPublicHoliday(CHRISTMAS, "FR")).thenReturn(true);

        HolidayException exception = new HolidayException();
        exception.setHolidayDate(CHRISTMAS);
        exception.setOpen(false); // Explicitly confirmed closed
        when(exceptionRepo.findByHolidayDate(CHRISTMAS)).thenReturn(Optional.of(exception));

        boolean result = service.isClosedForHoliday(CHRISTMAS, "FR", true);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Holiday + closedOnHolidays=false → salon open (feature disabled)")
    void holiday_notClosedOnHolidays_salonOpen() {
        // closedOnHolidays=false means the salon ignores holidays entirely
        boolean result = service.isClosedForHoliday(CHRISTMAS, "FR", false);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Not a holiday → salon open regardless of closedOnHolidays setting")
    void notAHoliday_salonOpen() {
        when(holidayService.isPublicHoliday(NORMAL_DAY, "FR")).thenReturn(false);

        boolean result = service.isClosedForHoliday(NORMAL_DAY, "FR", true);

        assertThat(result).isFalse();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Country-specific holidays ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Different countries have different holidays — Luxembourg vs France")
    void differentCountries_differentHolidays() {
        LocalDate luxembourgNationalDay = LocalDate.of(2026, 6, 23); // Luxembourg National Day

        // Luxembourg: June 23 is a holiday
        when(holidayService.isPublicHoliday(luxembourgNationalDay, "LU")).thenReturn(true);
        // France: June 23 is NOT a holiday
        when(holidayService.isPublicHoliday(luxembourgNationalDay, "FR")).thenReturn(false);

        when(exceptionRepo.findByHolidayDate(luxembourgNationalDay)).thenReturn(Optional.empty());

        boolean closedInLuxembourg = service.isClosedForHoliday(luxembourgNationalDay, "LU", true);
        boolean closedInFrance = service.isClosedForHoliday(luxembourgNationalDay, "FR", true);

        assertThat(closedInLuxembourg).isTrue();
        assertThat(closedInFrance).isFalse();
    }

    @Test
    @DisplayName("Belgium has different holidays than France")
    void belgium_vs_france() {
        LocalDate july21 = LocalDate.of(2026, 7, 21); // Belgian National Day

        when(holidayService.isPublicHoliday(july21, "BE")).thenReturn(true);
        when(holidayService.isPublicHoliday(july21, "FR")).thenReturn(false);

        when(exceptionRepo.findByHolidayDate(july21)).thenReturn(Optional.empty());

        assertThat(service.isClosedForHoliday(july21, "BE", true)).isTrue();
        assertThat(service.isClosedForHoliday(july21, "FR", true)).isFalse();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Edge cases ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Null country code → not closed (graceful)")
    void nullCountryCode_notClosed() {
        boolean result = service.isClosedForHoliday(CHRISTMAS, null, true);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Empty country code → not closed (graceful)")
    void emptyCountryCode_notClosed() {
        boolean result = service.isClosedForHoliday(CHRISTMAS, "", true);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Holiday on a Sunday (already closed) — no double effect")
    void holidayOnSunday_noDoubleEffect() {
        // Sunday Dec 25, 2022 was a Sunday — but we use a mock so any date works
        LocalDate sunday = LocalDate.of(2026, 12, 25); // Actually a Friday in 2026, but mock it

        when(holidayService.isPublicHoliday(sunday, "FR")).thenReturn(true);
        when(exceptionRepo.findByHolidayDate(sunday)).thenReturn(Optional.empty());

        // Holiday check says closed
        boolean closedForHoliday = service.isClosedForHoliday(sunday, "FR", true);
        assertThat(closedForHoliday).isTrue();

        // If the salon has no opening hours on Sunday, the SlotAvailabilityService
        // would return empty BEFORE even checking holidays.
        // Both paths (no hours + holiday) result in empty — no double effect, no crash.
    }

    @Test
    @DisplayName("New Year's Day — standard holiday check works")
    void newYearsDay_standardCheck() {
        LocalDate newYear = LocalDate.of(2027, 1, 1);

        when(holidayService.isPublicHoliday(newYear, "FR")).thenReturn(true);
        when(exceptionRepo.findByHolidayDate(newYear)).thenReturn(Optional.empty());

        assertThat(service.isClosedForHoliday(newYear, "FR", true)).isTrue();

        // With override: salon decides to open on New Year's
        HolidayException openException = new HolidayException();
        openException.setHolidayDate(newYear);
        openException.setOpen(true);
        when(exceptionRepo.findByHolidayDate(newYear)).thenReturn(Optional.of(openException));

        assertThat(service.isClosedForHoliday(newYear, "FR", true)).isFalse();
    }
}
