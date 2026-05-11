package com.luxpretty.app.common.error;

import lombok.Getter;

/**
 * Thrown when a client tries to book more appointments than the tenant's
 * BookingPolicy allows. Mapped to HTTP 409 with a typed error body by
 * GlobalExceptionHandler so the frontend can localize the message based on
 * {@link #code}.
 */
@Getter
public class BookingLimitExceededException extends RuntimeException {

    public static final String CODE_DAILY = "BOOKING_LIMIT_DAILY_EXCEEDED";
    public static final String CODE_NEW_CLIENT_WEEKLY = "BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED";

    private final String code;
    private final int limit;
    private final int currentCount;

    public BookingLimitExceededException(String code, int limit, int currentCount) {
        super(code + " (limit=" + limit + ", current=" + currentCount + ")");
        this.code = code;
        this.limit = limit;
        this.currentCount = currentCount;
    }
}
