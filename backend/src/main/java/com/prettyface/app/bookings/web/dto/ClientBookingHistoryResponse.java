package com.prettyface.app.bookings.web.dto;

public record ClientBookingHistoryResponse(
        Long id,
        Long bookingId,
        String tenantSlug,
        String salonName,
        String careName,
        Integer carePrice,
        Integer careDuration,
        String appointmentDate,
        String appointmentTime,
        String status,
        String createdAt
) {}
