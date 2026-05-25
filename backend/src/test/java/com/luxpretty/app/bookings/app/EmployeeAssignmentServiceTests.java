package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.domain.LeaveRequest;
import com.luxpretty.app.employee.domain.LeaveStatus;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.repo.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeAssignmentServiceTests {

    @Mock EmployeeRepository employeeRepo;
    @Mock LeaveRequestRepository leaveRepo;
    @Mock CareBookingRepository bookingRepo;
    @InjectMocks EmployeeAssignmentService service;

    private final LocalDate date = LocalDate.of(2026, 6, 1);
    private final LocalTime time = LocalTime.of(10, 0);
    private final Long careId = 7L;

    @Test
    void pickLeastLoaded_selectsEmployeeWithFewestBookingsThatDay() { // TC 15
        Employee marie = employee(1L, "Marie");
        Employee sophie = employee(2L, "Sophie");
        Employee lea = employee(3L, "Léa");
        when(employeeRepo.findActiveByAssignedCareId(careId)).thenReturn(List.of(marie, sophie, lea));
        when(leaveRepo.findApprovedLeavesCovering(List.of(1L, 2L, 3L), date)).thenReturn(List.of());
        when(bookingRepo.findActiveByDateAndEmployees(date, List.of(1L, 2L, 3L))).thenReturn(List.of());
        when(bookingRepo.countActiveByEmployeeAndDate(date)).thenReturn(List.of(
            new Object[]{2L, 2L}, // Sophie has 2 RDV today
            new Object[]{3L, 3L}  // Léa has 3 (Marie absent from list => 0)
        ));
        Employee chosen = service.pickLeastLoaded(date, time, careId);
        assertEquals(marie.getId(), chosen.getId());
    }

    @Test
    void pickLeastLoaded_alphaTieBreaker() { // TC 16
        Employee marie = employee(1L, "Marie");
        Employee sophie = employee(2L, "Sophie");
        when(employeeRepo.findActiveByAssignedCareId(careId)).thenReturn(List.of(sophie, marie));
        when(leaveRepo.findApprovedLeavesCovering(anyList(), eq(date))).thenReturn(List.of());
        when(bookingRepo.findActiveByDateAndEmployees(eq(date), anyList())).thenReturn(List.of());
        when(bookingRepo.countActiveByEmployeeAndDate(date)).thenReturn(List.of());
        Employee chosen = service.pickLeastLoaded(date, time, careId);
        assertEquals("Marie", chosen.getName());
    }

    @Test
    void pickLeastLoaded_allBusyAtSlot_throwsNoEmployeeAvailable() { // TC 17
        Employee marie = employee(1L, "Marie");
        when(employeeRepo.findActiveByAssignedCareId(careId)).thenReturn(List.of(marie));
        when(leaveRepo.findApprovedLeavesCovering(List.of(1L), date)).thenReturn(List.of());
        when(bookingRepo.findActiveByDateAndEmployees(date, List.of(1L))).thenReturn(List.of(bookingAt(marie, time)));
        assertThrows(NoEmployeeAvailableException.class,
            () -> service.pickLeastLoaded(date, time, careId));
    }

    @Test
    void pickLeastLoaded_employeeOnLeave_excluded() {
        Employee marie = employee(1L, "Marie");
        Employee sophie = employee(2L, "Sophie");
        when(employeeRepo.findActiveByAssignedCareId(careId)).thenReturn(List.of(marie, sophie));
        LeaveRequest marieLeave = new LeaveRequest();
        marieLeave.setEmployee(marie);
        marieLeave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepo.findApprovedLeavesCovering(List.of(1L, 2L), date)).thenReturn(List.of(marieLeave));
        when(bookingRepo.findActiveByDateAndEmployees(date, List.of(1L, 2L))).thenReturn(List.of());
        when(bookingRepo.countActiveByEmployeeAndDate(date)).thenReturn(List.of());
        Employee chosen = service.pickLeastLoaded(date, time, careId);
        assertEquals(sophie.getId(), chosen.getId());
    }

    @Test
    void pickLeastLoaded_zeroQualifiedEmployees_throwsNoEmployeeAvailable() {
        when(employeeRepo.findActiveByAssignedCareId(careId)).thenReturn(List.of());
        assertThrows(NoEmployeeAvailableException.class,
            () -> service.pickLeastLoaded(date, time, careId));
    }

    @Test
    void pickLeastLoaded_excludeSet_skipsExcluded() {
        Employee marie = employee(1L, "Marie");
        Employee sophie = employee(2L, "Sophie");
        when(employeeRepo.findActiveByAssignedCareId(careId)).thenReturn(List.of(marie, sophie));
        when(leaveRepo.findApprovedLeavesCovering(anyList(), eq(date))).thenReturn(List.of());
        when(bookingRepo.findActiveByDateAndEmployees(eq(date), anyList())).thenReturn(List.of());
        when(bookingRepo.countActiveByEmployeeAndDate(date)).thenReturn(List.of());
        Employee chosen = service.pickLeastLoaded(date, time, careId, Set.of(marie.getId()));
        assertEquals(sophie.getId(), chosen.getId());
    }

    private Employee employee(Long id, String name) {
        Employee e = new Employee();
        e.setId(id);
        e.setName(name);
        e.setActive(true);
        return e;
    }

    private CareBooking bookingAt(Employee e, LocalTime t) {
        CareBooking b = new CareBooking();
        b.setEmployeeId(e.getId());
        b.setAppointmentTime(t);
        Care care = new Care();
        care.setDuration(30);
        b.setCare(care);
        return b;
    }
}
