package com.luxpretty.app.employee.web.dto;

import com.luxpretty.app.employee.domain.LeaveType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record LeaveRequestDto(
    @NotNull LeaveType type,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    String reason
) {}
