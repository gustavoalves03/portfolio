package com.prettyface.app.bookings.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ClientBookingRequest(
        @NotNull Long careId,
        @NotNull LocalDate appointmentDate,
        @NotNull String appointmentTime  // "HH:mm" format
) {}
