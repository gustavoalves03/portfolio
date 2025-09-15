package com.fleurdecoquillage.app.bookings.web.dto;

import com.fleurdecoquillage.app.bookings.domain.CareBookingStatus;

import java.time.Instant;

public record CareBookingResponse(
        Long id,
        Long userId,
        Long careId,
        Integer quantity,
        CareBookingStatus status,
        Instant createdAt
) {}

