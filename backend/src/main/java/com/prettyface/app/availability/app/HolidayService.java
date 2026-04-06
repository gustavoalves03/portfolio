package com.prettyface.app.availability.app;

import de.focus_shift.jollyday.core.Holiday;
import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Year;
import java.util.*;

@Service
public class HolidayService {

    private final Map<String, HolidayManager> managerCache = new HashMap<>();

    public boolean isPublicHoliday(LocalDate date, String countryCode) {
        if (countryCode == null || countryCode.isBlank()) return false;
        HolidayManager mgr = getManager(countryCode);
        return mgr.isHoliday(date);
    }

    public List<HolidayInfo> getUpcomingHolidays(String countryCode, int count) {
        if (countryCode == null || countryCode.isBlank()) return List.of();
        HolidayManager mgr = getManager(countryCode);
        LocalDate today = LocalDate.now();
        Set<Holiday> holidays = new HashSet<>();
        holidays.addAll(mgr.getHolidays(Year.of(today.getYear())));
        holidays.addAll(mgr.getHolidays(Year.of(today.getYear() + 1)));
        return holidays.stream()
                .filter(h -> !h.getDate().isBefore(today))
                .sorted(Comparator.comparing(Holiday::getDate))
                .limit(count)
                .map(h -> new HolidayInfo(h.getDate(), h.getDescription(Locale.FRENCH)))
                .toList();
    }

    private HolidayManager getManager(String countryCode) {
        return managerCache.computeIfAbsent(countryCode.toLowerCase(),
                code -> HolidayManager.getInstance(ManagerParameters.create(code)));
    }

    public record HolidayInfo(LocalDate date, String name) {}
}
