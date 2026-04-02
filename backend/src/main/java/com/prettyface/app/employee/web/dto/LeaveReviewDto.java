package com.prettyface.app.employee.web.dto;

import com.prettyface.app.employee.domain.LeaveStatus;
import jakarta.validation.constraints.NotNull;

public record LeaveReviewDto(
    @NotNull LeaveStatus status,
    String reviewerNote
) {}
