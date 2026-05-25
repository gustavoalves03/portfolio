package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.repo.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmployeeAssignmentService {

    private final EmployeeRepository employeeRepo;
    private final LeaveRequestRepository leaveRepo;
    private final CareBookingRepository bookingRepo;

    public EmployeeAssignmentService(EmployeeRepository employeeRepo,
                                     LeaveRequestRepository leaveRepo,
                                     CareBookingRepository bookingRepo) {
        this.employeeRepo = employeeRepo;
        this.leaveRepo = leaveRepo;
        this.bookingRepo = bookingRepo;
    }

    @Transactional(readOnly = true)
    public Employee pickLeastLoaded(LocalDate date, LocalTime time, Long careId) {
        return pickLeastLoaded(date, time, careId, Set.of());
    }

    /**
     * Returns the qualified Employee with the fewest active bookings on the requested day,
     * excluding employees on approved leave or already busy at the requested slot.
     * Tie-breaker: alphabetical by name.
     *
     * @param excludeIds employee IDs already tried (used by the booking-create retry path
     *                   after a UK_BOOKING_SLOT collision)
     */
    @Transactional(readOnly = true)
    public Employee pickLeastLoaded(LocalDate date, LocalTime time, Long careId, Set<Long> excludeIds) {
        List<Employee> qualified = employeeRepo.findActiveByAssignedCareId(careId);
        if (qualified.isEmpty()) {
            throw new NoEmployeeAvailableException(careId, date, time);
        }

        List<Long> ids = qualified.stream().map(Employee::getId).toList();

        Set<Long> onLeave = leaveRepo.findApprovedLeavesCovering(ids, date).stream()
            .map(lr -> lr.getEmployee().getId())
            .collect(Collectors.toSet());

        List<CareBooking> dayBookings = bookingRepo.findActiveByDateAndEmployees(date, ids);

        Map<Long, Long> counts = new HashMap<>();
        bookingRepo.countActiveByEmployeeAndDate(date).forEach(row ->
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));

        List<Employee> candidates = qualified.stream()
            .filter(e -> !excludeIds.contains(e.getId()))
            .filter(e -> !onLeave.contains(e.getId()))
            .filter(e -> dayBookings.stream().noneMatch(b ->
                e.getId().equals(b.getEmployeeId()) && overlapsRequestedSlot(time, b)))
            .sorted(Comparator
                .comparingLong((Employee e) -> counts.getOrDefault(e.getId(), 0L))
                .thenComparing(Employee::getName))
            .toList();

        if (candidates.isEmpty()) {
            throw new NoEmployeeAvailableException(careId, date, time);
        }
        return candidates.get(0);
    }

    private boolean overlapsRequestedSlot(LocalTime requested, CareBooking b) {
        LocalTime bStart = b.getAppointmentTime();
        LocalTime bEnd = bStart.plusMinutes(b.getCare().getDuration());
        return requested.equals(bStart) || (requested.isAfter(bStart) && requested.isBefore(bEnd));
    }
}
