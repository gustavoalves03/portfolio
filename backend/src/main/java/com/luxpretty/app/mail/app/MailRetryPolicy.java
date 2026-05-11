package com.luxpretty.app.mail.app;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Exponential backoff for transient mail send failures.
 * <p>Attempt index is 1-based (first retry after attempt 1).
 * After {@link #MAX_ATTEMPTS} attempts the mail should be marked
 * {@code PERMANENTLY_FAILED} by the caller (not retried again).
 */
@Component
public class MailRetryPolicy {

    public static final int MAX_ATTEMPTS = 5;

    /**
     * Backoff delays at index i (0-based): 1m, 5m, 25m, 2h.
     * Index 4 (5th attempt) is terminal — no delay returned.
     */
    private static final List<Duration> BACKOFFS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(25),
            Duration.ofHours(2)
    );

    /**
     * Returns the timestamp at which the next attempt should be scheduled.
     * @param attemptCount how many attempts have been made so far (1..MAX_ATTEMPTS-1)
     * @param now reference timestamp (caller-supplied for testability)
     */
    public LocalDateTime nextAttemptAfter(int attemptCount, LocalDateTime now) {
        if (attemptCount < 1 || attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                "attemptCount must be in [1, " + (MAX_ATTEMPTS - 1) + "], got " + attemptCount);
        }
        return now.plus(BACKOFFS.get(attemptCount - 1));
    }
}
