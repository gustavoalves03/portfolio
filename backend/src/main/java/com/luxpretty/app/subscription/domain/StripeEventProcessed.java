package com.luxpretty.app.subscription.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Idempotency record for Stripe webhook events.
 *
 * <p>When Stripe retries a webhook (network errors, our 5xx response), it
 * sends the same {@code event.id}. We store every processed event_id with
 * its arrival timestamp; if we see the same id twice, the unique primary
 * key throws and we skip the handler safely.
 */
@Entity
@Table(name = "STRIPE_EVENTS_PROCESSED")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeEventProcessed {

    @Id
    @Column(name = "event_id", length = 255, nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }
}
