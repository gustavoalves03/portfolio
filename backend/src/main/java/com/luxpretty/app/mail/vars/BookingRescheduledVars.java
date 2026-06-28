package com.luxpretty.app.mail.vars;

import java.math.BigDecimal;

/**
 * Sent to the client when their booking date and/or time is changed.
 * Carries both the previous and the new schedule so the email can show
 * the move explicitly. Dates are pre-formatted to avoid JSON serialization
 * of temporals and keep rendering deterministic across worker restarts.
 */
public record BookingRescheduledVars(
        String clientName,
        String salonName,
        String careName,
        BigDecimal carePrice,
        String careDuration,
        String oldAppointmentDate,
        String oldAppointmentTime,
        String newAppointmentDate,
        String newAppointmentTime,
        Long bookingId,
        String dashboardUrl
) implements MailVars {}
