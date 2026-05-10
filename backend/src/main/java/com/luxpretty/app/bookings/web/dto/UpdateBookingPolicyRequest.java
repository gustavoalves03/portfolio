package com.luxpretty.app.bookings.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateBookingPolicyRequest(
        @NotNull @Min(1) @Max(10) Integer maxBookingsPerDayPerClient,
        @NotNull @Min(1) @Max(10) Integer maxBookingsPerWeekForNewClient
) {}
