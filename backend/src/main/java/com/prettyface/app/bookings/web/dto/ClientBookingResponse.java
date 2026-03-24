package com.prettyface.app.bookings.web.dto;

public record ClientBookingResponse(
        Long bookingId,
        String careName,
        Integer carePrice,
        Integer careDuration,
        String appointmentDate,
        String appointmentTime,
        String status,
        String salonName
) {}
