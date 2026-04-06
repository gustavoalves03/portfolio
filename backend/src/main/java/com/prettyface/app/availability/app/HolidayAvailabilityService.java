package com.prettyface.app.availability.app;

import com.prettyface.app.availability.repo.HolidayExceptionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class HolidayAvailabilityService {

    private final HolidayService holidayService;
    private final HolidayExceptionRepository exceptionRepo;

    public HolidayAvailabilityService(HolidayService holidayService,
                                       HolidayExceptionRepository exceptionRepo) {
        this.holidayService = holidayService;
        this.exceptionRepo = exceptionRepo;
    }

    /**
     * Check if the salon should be closed for a holiday on the given date.
     *
     * @param date              the date to check
     * @param countryCode       ISO 2-letter country code
     * @param closedOnHolidays  whether the salon has enabled the "closed on holidays" setting
     * @return true if the salon is closed for a holiday on this date
     */
    public boolean isClosedForHoliday(LocalDate date, String countryCode, boolean closedOnHolidays) {
        if (!closedOnHolidays) return false;
        if (!holidayService.isPublicHoliday(date, countryCode)) return false;
        // Check if there's an exception (salon open despite holiday)
        return exceptionRepo.findByHolidayDate(date)
                .map(ex -> !ex.isOpen())
                .orElse(true); // No exception = closed
    }
}
