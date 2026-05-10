package com.luxpretty.app.employee.web.dto;

import com.luxpretty.app.employee.domain.LeaveStatus;
import jakarta.validation.constraints.NotNull;

public record LeaveReviewDto(
    @NotNull LeaveStatus status,
    String reviewerNote
) {}
