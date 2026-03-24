package com.prettyface.app.availability.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OpeningHourRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull String openTime,
        @NotNull String closeTime
) {}
