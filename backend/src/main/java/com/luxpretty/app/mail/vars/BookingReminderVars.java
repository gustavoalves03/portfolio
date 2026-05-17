package com.luxpretty.app.mail.vars;

public record BookingReminderVars(
        String clientName,
        String salonName,
        String careName,
        String dateStr,
        String timeStr,
        String address,
        Long bookingId,
        String bookingUrl
) implements MailVars {}
