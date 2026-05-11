package com.luxpretty.app.mail.app;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailRetryPolicyTests {

    private final MailRetryPolicy policy = new MailRetryPolicy();

    @Test
    void attempt_1_delays_1_minute() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = policy.nextAttemptAfter(1, now);
        assertThat(Duration.between(now, next)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void attempt_2_delays_5_minutes() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(Duration.between(now, policy.nextAttemptAfter(2, now)))
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void attempt_3_delays_25_minutes() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(Duration.between(now, policy.nextAttemptAfter(3, now)))
                .isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void attempt_4_delays_2_hours() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(Duration.between(now, policy.nextAttemptAfter(4, now)))
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void attempt_5_is_terminal_throws() {
        // 5+ means we should have already marked PERMANENTLY_FAILED before calling
        assertThatThrownBy(() -> policy.nextAttemptAfter(5, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void max_attempts_is_5() {
        assertThat(MailRetryPolicy.MAX_ATTEMPTS).isEqualTo(5);
    }
}
