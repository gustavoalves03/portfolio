package com.luxpretty.app.mail.vars;

import java.math.BigDecimal;

/**
 * Sent to the client when their booking is confirmed.
 * Dates are pre-formatted to avoid JSON serialization of temporals
 * (and to keep rendering deterministic across worker restarts).
 */
public record BookingConfirmedVars(
        String clientName,
        String salonName,
        String careName,
        BigDecimal carePrice,
        String careDuration,
        String appointmentDate,
        String appointmentTime,
        Long bookingId,
        String dashboardUrl
) implements MailVars {}
