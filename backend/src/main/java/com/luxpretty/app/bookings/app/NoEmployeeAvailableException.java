package com.luxpretty.app.bookings.app;

import java.time.LocalDate;
import java.time.LocalTime;

public class NoEmployeeAvailableException extends RuntimeException {
    public final Long careId;
    public final LocalDate date;
    public final LocalTime time;

    public NoEmployeeAvailableException(Long careId, LocalDate date, LocalTime time) {
        super("No employee available for care " + careId + " at " + date + " " + time);
        this.careId = careId;
        this.date = date;
        this.time = time;
    }
}
