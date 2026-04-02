package com.prettyface.app.employee.web.dto;

import com.prettyface.app.employee.domain.LeaveStatus;
import com.prettyface.app.employee.domain.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveResponse(
    Long id,
    Long employeeId,
    String employeeName,
    LeaveType type,
    LeaveStatus status,
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    boolean hasDocument,
    String reviewerNote,
    LocalDateTime createdAt,
    LocalDateTime reviewedAt
) {}
