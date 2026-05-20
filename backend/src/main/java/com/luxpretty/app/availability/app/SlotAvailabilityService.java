package com.luxpretty.app.availability.app;


import com.luxpretty.app.common.error.ResourceNotFoundException;
import com.luxpretty.app.availability.domain.BlockedSlot;
import com.luxpretty.app.availability.domain.OpeningHour;
import com.luxpretty.app.availability.repo.BlockedSlotRepository;
import com.luxpretty.app.availability.repo.OpeningHourRepository;
import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.bookings.web.dto.EmployeeSlotState;
import com.luxpretty.app.bookings.web.dto.SlotWithEmployees;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.employee.app.LeaveRequestService;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.repo.LeaveRequestRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SlotAvailabilityService {

    private static final int SLOT_INTERVAL_MINUTES = 30;

    private final OpeningHourRepository openingHourRepo;
    private final BlockedSlotRepository blockedSlotRepo;
    private final CareBookingRepository bookingRepo;
    private final CareRepository careRepo;
    private final LeaveRequestService leaveRequestService;
    private final HolidayAvailabilityService holidayAvailabilityService;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public SlotAvailabilityService(OpeningHourRepository openingHourRepo,
                                   BlockedSlotRepository blockedSlotRepo,
                                   CareBookingRepository bookingRepo,
                                   CareRepository careRepo,
                                   LeaveRequestService leaveRequestService,
                                   HolidayAvailabilityService holidayAvailabilityService,
                                   TenantRepository tenantRepository,
                                   EmployeeRepository employeeRepository,
                                   LeaveRequestRepository leaveRequestRepository) {
        this.openingHourRepo = openingHourRepo;
        this.blockedSlotRepo = blockedSlotRepo;
        this.bookingRepo = bookingRepo;
        this.careRepo = careRepo;
        this.leaveRequestService = leaveRequestService;
        this.holidayAvailabilityService = holidayAvailabilityService;
        this.tenantRepository = tenantRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    /**
     * Compute available time slots for a given date and care service.
     * Takes into account opening hours, blocked slots, and existing bookings.
     */
    @Transactional(readOnly = true)
    public List<TimeSlot> getAvailableSlots(LocalDate date, Long careId) {
        if (date.isBefore(LocalDate.now())) {
            return List.of();
        }

        // Check if closed for public holiday
        if (isClosedForHoliday(date)) {
            return List.of();
        }

        Care care = careRepo.findById(careId)
                .orElseThrow(() -> new ResourceNotFoundException("Care not found: " + careId));
        int durationMinutes = care.getDuration();

        // Get day of week (Monday=1..Sunday=7)
        int dow = date.getDayOfWeek().getValue();

        // Get opening hours for this day
        List<OpeningHour> openingHours = openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()
                .stream()
                .filter(oh -> oh.getDayOfWeek() == dow)
                .toList();

        if (openingHours.isEmpty()) {
            return List.of(); // Closed day
        }

        // Get blocked slots for this date
        List<BlockedSlot> blockedSlots = blockedSlotRepo
                .findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(date)
                .stream()
                .filter(bs -> bs.getDate().equals(date))
                .toList();

        // Check if full day is blocked
        boolean fullDayBlocked = blockedSlots.stream().anyMatch(BlockedSlot::isFullDay);
        if (fullDayBlocked) {
            return List.of();
        }

        // Get existing bookings for this date (exclude cancelled)
        List<CareBooking> existingBookings = bookingRepo
                .findByAppointmentDateAndStatusNot(date, CareBookingStatus.CANCELLED);

        // Get buffer time between appointments
        int bufferMinutes = getBufferMinutes();

        // Generate candidate slots from opening hours (deduplicate overlapping windows)
        List<TimeSlot> availableSlots = new ArrayList<>();
        java.util.Set<String> seenStartTimes = new java.util.HashSet<>();

        for (OpeningHour oh : openingHours) {
            LocalTime cursor = oh.getOpenTime();
            LocalTime windowEnd = oh.getCloseTime();

            while (cursor.plusMinutes(durationMinutes).compareTo(windowEnd) <= 0) {
                LocalTime slotEnd = cursor.plusMinutes(durationMinutes);
                String startKey = cursor.toString();

                if (!seenStartTimes.contains(startKey)
                        && !isBlockedAt(cursor, slotEnd, blockedSlots)
                        && !isBookedAt(cursor, slotEnd, existingBookings, bufferMinutes)) {
                    availableSlots.add(new TimeSlot(startKey, slotEnd.toString()));
                }
                seenStartTimes.add(startKey);

                cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
            }
        }

        return availableSlots;
    }

    /**
     * Compute available time slots for a given date, care service, and specific employee.
     * Takes into account the employee's personal opening hours (or salon-wide fallback),
     * employee-specific and salon-wide blocked slots, employee leave, and existing bookings.
     */
    @Transactional(readOnly = true)
    public List<TimeSlot> getAvailableSlots(LocalDate date, Long careId, Long employeeId) {
        if (date.isBefore(LocalDate.now())) {
            return List.of();
        }

        // Check if closed for public holiday
        if (isClosedForHoliday(date)) {
            return List.of();
        }

        Care care = careRepo.findById(careId)
                .orElseThrow(() -> new ResourceNotFoundException("Care not found: " + careId));
        int durationMinutes = care.getDuration();

        // Get day of week (Monday=1..Sunday=7)
        int dow = date.getDayOfWeek().getValue();

        // Get salon-wide opening hours for this day (always needed for clipping)
        List<OpeningHour> salonHours = openingHourRepo
                .findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc()
                .stream()
                .filter(oh -> oh.getDayOfWeek() == dow)
                .toList();

        // If the salon itself is closed, no employee can work
        if (salonHours.isEmpty()) {
            return List.of();
        }

        // Get employee's personal opening hours; fallback to salon-wide if none
        List<OpeningHour> employeeHours = openingHourRepo
                .findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(employeeId);
        List<OpeningHour> openingHours;
        if (employeeHours.isEmpty()) {
            openingHours = salonHours;
        } else {
            // Clip employee hours to salon hours (intersection)
            List<OpeningHour> empDayHours = employeeHours.stream()
                    .filter(oh -> oh.getDayOfWeek() == dow)
                    .toList();
            openingHours = clipToSalonHours(empDayHours, salonHours);
        }

        if (openingHours.isEmpty()) {
            return List.of(); // No overlap between employee and salon hours
        }

        // Check if employee is on approved leave
        if (leaveRequestService.isOnLeave(employeeId, date)) {
            return List.of();
        }

        // Get employee-specific blocked slots AND salon-wide blocked slots for this date
        List<BlockedSlot> employeeBlocks = blockedSlotRepo
                .findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(employeeId, date)
                .stream()
                .filter(bs -> bs.getDate().equals(date))
                .toList();
        List<BlockedSlot> salonBlocks = blockedSlotRepo
                .findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(date)
                .stream()
                .filter(bs -> bs.getDate().equals(date) && bs.getEmployeeId() == null)
                .toList();
        List<BlockedSlot> allBlocks = Stream.concat(employeeBlocks.stream(), salonBlocks.stream()).toList();

        // Check if full day is blocked
        boolean fullDayBlocked = allBlocks.stream().anyMatch(BlockedSlot::isFullDay);
        if (fullDayBlocked) {
            return List.of();
        }

        // Get existing bookings for this date and employee (exclude cancelled)
        List<CareBooking> existingBookings = bookingRepo
                .findByAppointmentDateAndEmployeeIdAndStatusNot(date, employeeId, CareBookingStatus.CANCELLED);

        // Get buffer time between appointments
        int bufferMinutes = getBufferMinutes();

        // Generate candidate slots from opening hours (deduplicate overlapping windows)
        List<TimeSlot> availableSlots = new ArrayList<>();
        java.util.Set<String> seenStartTimes = new java.util.HashSet<>();

        for (OpeningHour oh : openingHours) {
            LocalTime cursor = oh.getOpenTime();
            LocalTime windowEnd = oh.getCloseTime();

            while (cursor.plusMinutes(durationMinutes).compareTo(windowEnd) <= 0) {
                LocalTime slotEnd = cursor.plusMinutes(durationMinutes);
                String startKey = cursor.toString();

                if (!seenStartTimes.contains(startKey)
                        && !isBlockedAt(cursor, slotEnd, allBlocks)
                        && !isBookedAt(cursor, slotEnd, existingBookings, bufferMinutes)) {
                    availableSlots.add(new TimeSlot(startKey, slotEnd.toString()));
                }
                seenStartTimes.add(startKey);

                cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
            }
        }

        return availableSlots;
    }

    /**
     * For each candidate time slot on the given date, returns a list of all qualified employees
     * with their individual availability state (available, BUSY, ON_LEAVE).
     * Slots where no qualified employee is available are pruned from the result.
     */
    @Transactional(readOnly = true)
    public List<SlotWithEmployees> getAvailableSlotsForCareWithEmployees(LocalDate date, Long careId) {
        if (date.isBefore(LocalDate.now())) return List.of();
        if (isClosedForHoliday(date)) return List.of();

        Care care = careRepo.findById(careId)
                .orElseThrow(() -> new ResourceNotFoundException("Care not found: " + careId));

        // Qualified active employees only (strict mode)
        List<Employee> qualified = employeeRepository.findActiveByAssignedCareId(careId);
        if (qualified.isEmpty()) return List.of();

        List<Long> employeeIds = qualified.stream().map(Employee::getId).toList();

        // Approved leaves covering this date (batch fetch)
        Set<Long> onLeaveIds = leaveRequestRepository.findApprovedLeavesCovering(employeeIds, date)
                .stream()
                .map(lr -> lr.getEmployee().getId())
                .collect(Collectors.toSet());

        // Active bookings (PENDING/CONFIRMED) for these employees on this date
        List<CareBooking> activeBookings = bookingRepo.findActiveByDateAndEmployees(date, employeeIds);

        // Candidate time windows (salon-wide: opening hours, blocked slots, holidays)
        List<TimeSlot> candidateTimes = computeSalonTimeWindows(date, care.getDuration());

        int bufferMinutes = getBufferMinutes();
        List<SlotWithEmployees> result = new ArrayList<>();

        for (TimeSlot slot : candidateTimes) {
            LocalTime slotStart = LocalTime.parse(slot.startTime());
            LocalTime slotEnd = LocalTime.parse(slot.endTime());

            List<EmployeeSlotState> perEmployee = qualified.stream().map(e -> {
                if (onLeaveIds.contains(e.getId())) {
                    return EmployeeSlotState.onLeave(e.getId(), e.getName());
                }
                boolean busy = activeBookings.stream().anyMatch(b ->
                        b.getEmployeeId() != null && b.getEmployeeId().equals(e.getId())
                        && overlaps(slotStart, slotEnd,
                                    b.getAppointmentTime(),
                                    b.getAppointmentTime().plusMinutes(
                                            b.getCare().getDuration() + bufferMinutes)));
                return busy ? EmployeeSlotState.busy(e.getId(), e.getName())
                            : EmployeeSlotState.available(e.getId(), e.getName());
            }).toList();

            // Only include slot if at least one employee is available
            if (perEmployee.stream().anyMatch(EmployeeSlotState::available)) {
                result.add(new SlotWithEmployees(slot.startTime(), perEmployee));
            }
        }
        return result;
    }

    /**
     * Compute candidate time windows from salon-wide opening hours, excluding salon-wide
     * blocked slots. Employee-specific availability is handled separately in
     * {@link #getAvailableSlotsForCareWithEmployees}.
     */
    private List<TimeSlot> computeSalonTimeWindows(LocalDate date, int careDuration) {
        int dow = date.getDayOfWeek().getValue();

        List<OpeningHour> openingHours = openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()
                .stream()
                .filter(oh -> oh.getDayOfWeek() == dow)
                .filter(oh -> oh.getEmployeeId() == null) // salon-wide only
                .toList();
        if (openingHours.isEmpty()) return List.of();

        List<BlockedSlot> blockedSlots = blockedSlotRepo
                .findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(date)
                .stream()
                .filter(bs -> bs.getDate().equals(date))
                .filter(bs -> bs.getEmployeeId() == null) // salon-wide only
                .toList();
        if (blockedSlots.stream().anyMatch(BlockedSlot::isFullDay)) return List.of();

        List<TimeSlot> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (OpeningHour oh : openingHours) {
            LocalTime cursor = oh.getOpenTime();
            LocalTime windowEnd = oh.getCloseTime();

            while (cursor.plusMinutes(careDuration).compareTo(windowEnd) <= 0) {
                LocalTime slotEnd = cursor.plusMinutes(careDuration);
                String key = cursor.toString();
                if (!seen.contains(key) && !isBlockedAt(cursor, slotEnd, blockedSlots)) {
                    result.add(new TimeSlot(key, slotEnd.toString()));
                }
                seen.add(key);
                cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
            }
        }
        return result;
    }

    private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    /**
     * Find the first available employee for a specific time slot from a list of candidates.
     * Returns the employee ID of the first available employee, or null if none are available.
     */
    @Transactional(readOnly = true)
    public Long findFirstAvailableEmployee(LocalDate date, Long careId, String timeStr,
                                           List<Long> candidateEmployeeIds) {
        LocalTime requestedTime = LocalTime.parse(timeStr);

        for (Long employeeId : candidateEmployeeIds) {
            List<TimeSlot> slots = getAvailableSlots(date, careId, employeeId);
            boolean available = slots.stream()
                    .anyMatch(slot -> LocalTime.parse(slot.startTime()).equals(requestedTime));
            if (available) {
                return employeeId;
            }
        }
        return null;
    }

    /**
     * Get the buffer time (in minutes) between appointments for the current tenant.
     */
    private int getBufferMinutes() {
        String slug = TenantContext.getCurrentTenant();
        if (slug == null) return 0;
        return tenantRepository.findBySlug(slug)
                .map(t -> t.getBufferMinutes() != null ? t.getBufferMinutes() : 0)
                .orElse(0);
    }

    /**
     * Clip employee opening hours to salon opening hours (intersection).
     * An employee can only work during times when the salon is also open.
     * For each employee window, find the overlap with each salon window.
     */
    private List<OpeningHour> clipToSalonHours(List<OpeningHour> empHours, List<OpeningHour> salonHours) {
        List<OpeningHour> clipped = new ArrayList<>();
        for (OpeningHour emp : empHours) {
            for (OpeningHour salon : salonHours) {
                // Find intersection of [emp.open, emp.close] and [salon.open, salon.close]
                LocalTime start = emp.getOpenTime().isAfter(salon.getOpenTime()) ? emp.getOpenTime() : salon.getOpenTime();
                LocalTime end = emp.getCloseTime().isBefore(salon.getCloseTime()) ? emp.getCloseTime() : salon.getCloseTime();
                if (start.isBefore(end)) {
                    OpeningHour oh = new OpeningHour();
                    oh.setDayOfWeek(emp.getDayOfWeek());
                    oh.setOpenTime(start);
                    oh.setCloseTime(end);
                    oh.setEmployeeId(emp.getEmployeeId());
                    clipped.add(oh);
                }
            }
        }
        return clipped;
    }

    /**
     * Check if the salon is closed for a public holiday on the given date.
     * Uses TenantContext to resolve the current tenant's country and closedOnHolidays flag.
     */
    private boolean isClosedForHoliday(LocalDate date) {
        String slug = TenantContext.getCurrentTenant();
        if (slug == null) return false;
        Tenant tenant = tenantRepository.findBySlug(slug).orElse(null);
        if (tenant == null) return false;
        String country = tenant.getAddressCountry();
        Boolean closed = tenant.getClosedOnHolidays();
        if (country == null || closed == null) return false;
        return holidayAvailabilityService.isClosedForHoliday(date, country, closed);
    }

    private boolean isBlockedAt(LocalTime start, LocalTime end, List<BlockedSlot> blockedSlots) {
        for (BlockedSlot bs : blockedSlots) {
            if (bs.isFullDay()) return true;
            if (bs.getStartTime() != null && bs.getEndTime() != null) {
                // Overlap check
                if (start.isBefore(bs.getEndTime()) && end.isAfter(bs.getStartTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBookedAt(LocalTime start, LocalTime end, List<CareBooking> bookings, int bufferMinutes) {
        for (CareBooking booking : bookings) {
            LocalTime bookingStart = booking.getAppointmentTime();
            int bookingDuration = booking.getCare().getDuration();
            // Add buffer after the booking: the next slot can only start after bookingEnd + buffer
            LocalTime bookingEndWithBuffer = bookingStart.plusMinutes(bookingDuration + bufferMinutes);

            // Overlap check (buffer extends the "occupied" zone)
            if (start.isBefore(bookingEndWithBuffer) && end.isAfter(bookingStart)) {
                return true;
            }
        }
        return false;
    }

    public record TimeSlot(String startTime, String endTime) {}
}
