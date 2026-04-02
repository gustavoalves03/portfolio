package com.prettyface.app.employee.repo;

import com.prettyface.app.employee.domain.LeaveRequest;
import com.prettyface.app.employee.domain.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);
    List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveStatus status, LocalDate endDate, LocalDate startDate);
}
