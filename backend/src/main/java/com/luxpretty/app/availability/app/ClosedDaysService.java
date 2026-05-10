package com.luxpretty.app.availability.app;

import com.luxpretty.app.availability.domain.BlockedSlot;
import com.luxpretty.app.availability.domain.OpeningHour;
import com.luxpretty.app.availability.repo.BlockedSlotRepository;
import com.luxpretty.app.availability.repo.OpeningHourRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ClosedDaysService {

    public enum ClosedDayReason {
        HOLIDAY,
        WEEKLY_CLOSED,
        FULL_DAY_BLOCK,
        TODAY_CLOSED,
        PAST,
    }

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
     * Return all dates in [from, to] (inclusive) with the reason they are closed.
     *
     * Priority when multiple reasons apply: HOLIDAY > FULL_DAY_BLOCK > WEEKLY_CLOSED >
     * TODAY_CLOSED > PAST. The most informative reason wins so the UI can color
     * holidays distinctly even when they fall on a weekly-closed day.
     *
     * Per-slot constraints (bookings, employee leaves) are intentionally not
     * considered here — those reduce *available slots*, not whether the day itself
     * is open.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, ClosedDayReason> getClosedDays(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return Map.of();
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

        Map<LocalDate, ClosedDayReason> closed = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            ClosedDayReason reason = classify(d, today, now, latestCloseByDow, fullDayBlocks, holidayCtx);
            if (reason != null) {
                closed.put(d, reason);
            }
        }
        return closed;
    }

    private ClosedDayReason classify(LocalDate date,
                                      LocalDate today,
                                      LocalTime now,
                                      Map<Integer, LocalTime> latestCloseByDow,
                                      Set<LocalDate> fullDayBlocks,
                                      TenantHolidayContext holidayCtx) {
        if (holidayCtx != null && holidayAvailabilityService.isClosedForHoliday(
                date, holidayCtx.country(), holidayCtx.closedOnHolidays())) {
            return ClosedDayReason.HOLIDAY;
        }
        if (fullDayBlocks.contains(date)) {
            return ClosedDayReason.FULL_DAY_BLOCK;
        }
        int dow = date.getDayOfWeek().getValue();
        LocalTime latestClose = latestCloseByDow.get(dow);
        if (latestClose == null) {
            return ClosedDayReason.WEEKLY_CLOSED;
        }
        if (date.isBefore(today)) {
            return ClosedDayReason.PAST;
        }
        if (date.equals(today) && !now.isBefore(latestClose)) {
            return ClosedDayReason.TODAY_CLOSED;
        }
        return null;
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
