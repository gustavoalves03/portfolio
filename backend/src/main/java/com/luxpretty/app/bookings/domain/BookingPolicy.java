package com.luxpretty.app.bookings.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BOOKING_POLICY")
public class BookingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_bookings_per_day_per_client", nullable = false)
    private Integer maxBookingsPerDayPerClient;

    @Column(name = "max_bookings_per_week_for_new_client", nullable = false)
    private Integer maxBookingsPerWeekForNewClient;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
