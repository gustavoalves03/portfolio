package com.prettyface.app.bookings.web.dto;

import com.prettyface.app.bookings.domain.CareBookingStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CareBookingRequest(
        @NotNull Long userId,
        @NotNull Long careId,
        @NotNull @Min(1) Integer quantity,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime appointmentTime,
        @NotNull CareBookingStatus status
) {}

