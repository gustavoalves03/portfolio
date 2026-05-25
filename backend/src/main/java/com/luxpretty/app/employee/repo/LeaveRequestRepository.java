package com.luxpretty.app.employee.repo;

import com.luxpretty.app.employee.domain.LeaveRequest;
import com.luxpretty.app.employee.domain.LeaveStatus;
import com.luxpretty.app.employee.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);
    List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveStatus status, LocalDate endDate, LocalDate startDate);
    List<LeaveRequest> findByStatusInOrderByReviewedAtDesc(List<LeaveStatus> statuses);
    List<LeaveRequest> findByStatusInAndTypeOrderByReviewedAtDesc(List<LeaveStatus> statuses, LeaveType type);

    @Query("SELECT lr FROM LeaveRequest lr " +
           "WHERE lr.status = com.luxpretty.app.employee.domain.LeaveStatus.APPROVED " +
           "AND lr.employee.id IN :employeeIds " +
           "AND lr.startDate <= :date AND lr.endDate >= :date")
    List<LeaveRequest> findApprovedLeavesCovering(@Param("employeeIds") Collection<Long> employeeIds,
                                                  @Param("date") LocalDate date);
}
