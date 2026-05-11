package com.luxpretty.app.mail.vars;

import java.math.BigDecimal;

/**
 * Sent to the salon (pro) when a new booking is created by a client.
 */
public record BookingReceivedProVars(
        String proName,
        String clientName,
        String careName,
        BigDecimal carePrice,
        String careDuration,
        String appointmentDate,
        String appointmentTime,
        Long bookingId,
        String dashboardUrl
) implements MailVars {}
