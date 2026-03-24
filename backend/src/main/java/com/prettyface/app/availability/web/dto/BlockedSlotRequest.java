package com.prettyface.app.availability.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BlockedSlotRequest(
        @NotNull LocalDate date,
        String startTime,
        String endTime,
        boolean fullDay,
        String reason
) {}
