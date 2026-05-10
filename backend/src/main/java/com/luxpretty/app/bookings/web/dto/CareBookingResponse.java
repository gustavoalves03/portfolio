package com.luxpretty.app.bookings.web.dto;

import com.luxpretty.app.bookings.domain.CareBookingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CareBookingResponse(
        Long id,
        Long userId,
        Long careId,
        Integer quantity,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        CareBookingStatus status,
        LocalDateTime createdAt
) {}

