package com.luxpretty.app.employee.repo;

import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.domain.LeaveRequest;
import com.luxpretty.app.employee.domain.LeaveStatus;
import com.luxpretty.app.employee.domain.LeaveType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class LeaveRequestRepositoryTests {

    private static final AtomicLong USER_ID_SEQ = new AtomicLong(1);

    @Autowired
    LeaveRequestRepository repo;

    @Autowired
    EmployeeRepository employeeRepo;

    private Employee persistEmployee(String name) {
        Employee e = new Employee();
        e.setName(name);
        e.setEmail(name.toLowerCase() + "@test.com");
        e.setUserId(USER_ID_SEQ.getAndIncrement());
        e.setActive(true);
        return employeeRepo.save(e);
    }

    private LeaveRequest persistLeave(Employee employee, LeaveStatus status,
                                      LocalDate start, LocalDate end) {
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setType(LeaveType.VACATION);
        lr.setStatus(status);
        lr.setStartDate(start);
        lr.setEndDate(end);
        return repo.save(lr);
    }

    @Test
    void findApprovedLeavesCovering_returnsOnlyApprovedLeavesThatCoverDate() {
        Employee marie = persistEmployee("Marie");
        Employee sophie = persistEmployee("Sophie");

        persistLeave(marie,  LeaveStatus.APPROVED, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)); // covers 6/2
        persistLeave(sophie, LeaveStatus.PENDING,  LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2)); // PENDING -> excluded
        persistLeave(sophie, LeaveStatus.APPROVED, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 2)); // covers 6/2

        List<LeaveRequest> covering = repo.findApprovedLeavesCovering(
                List.of(marie.getId(), sophie.getId()),
                LocalDate.of(2026, 6, 2));

        assertEquals(2, covering.size());
        Set<Long> ids = covering.stream()
                .map(lr -> lr.getEmployee().getId())
                .collect(Collectors.toSet());
        assertTrue(ids.contains(marie.getId()));
        assertTrue(ids.contains(sophie.getId()));
        // Ensure no PENDING leak
        assertTrue(covering.stream().allMatch(lr -> lr.getStatus() == LeaveStatus.APPROVED));
    }

    @Test
    void findApprovedLeavesCovering_excludesLeaveStartingAfterQueriedDate() {
        Employee marie = persistEmployee("Marie");
        persistLeave(marie, LeaveStatus.APPROVED, LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 5));

        List<LeaveRequest> covering = repo.findApprovedLeavesCovering(
                List.of(marie.getId()), LocalDate.of(2026, 6, 2));

        assertTrue(covering.isEmpty(), "leave starting AFTER query date must not be returned");
    }

    @Test
    void findApprovedLeavesCovering_excludesLeavesOfEmployeesNotInList() {
        Employee marie = persistEmployee("Marie");
        Employee sophie = persistEmployee("Sophie");
        persistLeave(sophie, LeaveStatus.APPROVED, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));

        List<LeaveRequest> covering = repo.findApprovedLeavesCovering(
                List.of(marie.getId()), LocalDate.of(2026, 6, 2));

        assertTrue(covering.isEmpty(), "leave of an employee not in the queried list must not be returned");
    }
}
