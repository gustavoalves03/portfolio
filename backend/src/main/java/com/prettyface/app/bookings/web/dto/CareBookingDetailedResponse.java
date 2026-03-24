package com.prettyface.app.bookings.web.dto;

import com.prettyface.app.bookings.domain.CareBookingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CareBookingDetailedResponse(
    Long id,
    UserInfo user,
    CareInfo care,
    Integer quantity,
    LocalDate appointmentDate,
    LocalTime appointmentTime,
    CareBookingStatus status,
    Instant createdAt
) {}
