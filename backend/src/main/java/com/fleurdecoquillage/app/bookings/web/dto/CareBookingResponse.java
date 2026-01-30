package com.fleurdecoquillage.app.bookings.web.dto;

import com.fleurdecoquillage.app.bookings.domain.CareBookingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CareBookingResponse(
        Long id,
        Long userId,
        Long careId,
        Integer quantity,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        CareBookingStatus status,
        Instant createdAt
) {}

