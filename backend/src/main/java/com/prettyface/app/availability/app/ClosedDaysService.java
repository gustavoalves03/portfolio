package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.BlockedSlot;
import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.BlockedSlotRepository;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ClosedDaysService {

    private final OpeningHourRepository openingHourRepo;
    private final BlockedSlotRepository blockedSlotRepo;
    private final HolidayAvailabilityService holidayAvailabilityService;
    private final TenantRepository tenantRepository;
    private final Clock clock;

    public ClosedDaysService(OpeningHourRepository openingHourRepo,
                             BlockedSlotRepository blockedSlotRepo,
                             HolidayAvailabilityService holidayAvailabilityService,
                             TenantRepository tenantRepository,
                             Clock clock) {
        this.openingHourRepo = openingHourRepo;
        this.blockedSlotRepo = blockedSlotRepo;
        this.holidayAvailabilityService = holidayAvailabilityService;
        this.tenantRepository = tenantRepository;
        this.clock = clock;
    }

    /**
     * Return all dates in [from, to] (inclusive) on which the salon is structurally closed:
     * - past dates (before today)
     * - weekly closed (no opening hours for that day-of-week)
     * - public holiday with closedOnHolidays enabled (unless an exception flips it open)
     * - full-day blocked slot
     * - today, when current time is past the latest closeTime of the day's opening hours
     *
     * Per-slot constraints (bookings, employee leaves) are intentionally not considered here —
     * those reduce *available slots*, not whether the day itself is open.
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> getClosedDays(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return Set.of();
        }

        Map<Integer, LocalTime> latestCloseByDow = new HashMap<>();
        for (OpeningHour oh : openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()) {
            int dow = oh.getDayOfWeek();
            LocalTime currentLatest = latestCloseByDow.get(dow);
            if (currentLatest == null || oh.getCloseTime().isAfter(currentLatest)) {
                latestCloseByDow.put(dow, oh.getCloseTime());
            }
        }

        Set<LocalDate> fullDayBlocks = new HashSet<>();
        for (BlockedSlot bs : blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(from)) {
            if (bs.isFullDay() && !bs.getDate().isAfter(to)) {
                fullDayBlocks.add(bs.getDate());
            }
        }

        TenantHolidayContext holidayCtx = resolveHolidayContext();
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);

        Set<LocalDate> closed = new HashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isClosed(d, today, now, latestCloseByDow, fullDayBlocks, holidayCtx)) {
                closed.add(d);
            }
        }
        return closed;
    }

    private boolean isClosed(LocalDate date,
                              LocalDate today,
                              LocalTime now,
                              Map<Integer, LocalTime> latestCloseByDow,
                              Set<LocalDate> fullDayBlocks,
                              TenantHolidayContext holidayCtx) {
        if (date.isBefore(today)) return true;
        int dow = date.getDayOfWeek().getValue();
        LocalTime latestClose = latestCloseByDow.get(dow);
        if (latestClose == null) return true;
        if (fullDayBlocks.contains(date)) return true;
        if (holidayCtx != null && holidayAvailabilityService.isClosedForHoliday(
                date, holidayCtx.country(), holidayCtx.closedOnHolidays())) {
            return true;
        }
        if (date.equals(today) && !now.isBefore(latestClose)) return true;
        return false;
    }

    private TenantHolidayContext resolveHolidayContext() {
        String slug = TenantContext.getCurrentTenant();
        if (slug == null) return null;
        Tenant tenant = tenantRepository.findBySlug(slug).orElse(null);
        if (tenant == null) return null;
        String country = tenant.getAddressCountry();
        Boolean closed = tenant.getClosedOnHolidays();
        if (country == null || closed == null) return null;
        return new TenantHolidayContext(country, closed);
    }

    private record TenantHolidayContext(String country, boolean closedOnHolidays) {}
}
