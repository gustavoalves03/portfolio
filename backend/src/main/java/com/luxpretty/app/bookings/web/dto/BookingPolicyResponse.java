package com.luxpretty.app.bookings.web.dto;

import java.time.LocalDateTime;

public record BookingPolicyResponse(
        Integer maxBookingsPerDayPerClient,
        Integer maxBookingsPerWeekForNewClient,
        LocalDateTime updatedAt
) {}
