package com.prettyface.app.employee.app;

import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.domain.LeaveRequest;
import com.prettyface.app.employee.domain.LeaveStatus;
import com.prettyface.app.employee.domain.LeaveType;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.repo.LeaveRequestRepository;
import com.prettyface.app.employee.web.dto.LeaveRequestDto;
import com.prettyface.app.employee.web.dto.LeaveResponse;
import com.prettyface.app.employee.web.dto.LeaveReviewDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaveRequestService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional
    public LeaveResponse createLeave(Long employeeId, LeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        if (dto.endDate().isBefore(dto.startDate())) {
            throw new IllegalArgumentException("End date must be after start date or the same day");
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setType(dto.type());
        leave.setStartDate(dto.startDate());
        leave.setEndDate(dto.endDate());
        leave.setReason(dto.reason());
        leave.setStatus(LeaveStatus.PENDING);

        LeaveRequest saved = leaveRequestRepository.save(leave);
        return toResponse(saved);
    }

    @Transactional
    public LeaveResponse reviewLeave(Long leaveId, LeaveReviewDto dto) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Leave request has already been reviewed");
        }

        leave.setStatus(dto.status());
        leave.setReviewerNote(dto.reviewerNote());
        leave.setReviewedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(leave);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> listPending() {
        return leaveRequestRepository.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> listReviewed(LeaveType typeFilter) {
        List<LeaveStatus> reviewedStatuses = List.of(LeaveStatus.APPROVED, LeaveStatus.REJECTED);
        List<LeaveRequest> results;
        if (typeFilter != null) {
            results = leaveRequestRepository.findByStatusInAndTypeOrderByReviewedAtDesc(reviewedStatuses, typeFilter);
        } else {
            results = leaveRequestRepository.findByStatusInOrderByReviewedAtDesc(reviewedStatuses);
        }
        return results.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> listByEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public boolean isOnLeave(Long employeeId, LocalDate date) {
        return !leaveRequestRepository
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, LeaveStatus.APPROVED, date, date)
                .isEmpty();
    }

    @Transactional
    public LeaveResponse attachDocument(Long leaveId, String documentPath) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + leaveId));

        leave.setDocumentPath(documentPath);
        LeaveRequest saved = leaveRequestRepository.save(leave);
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private LeaveResponse toResponse(LeaveRequest leave) {
        Employee emp = leave.getEmployee();
        return new LeaveResponse(
                leave.getId(),
                emp.getId(),
                emp.getName(),
                leave.getType(),
                leave.getStatus(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getReason(),
                leave.getDocumentPath() != null,
                leave.getReviewerNote(),
                leave.getCreatedAt(),
                leave.getReviewedAt()
        );
    }
}
