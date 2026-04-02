package com.prettyface.app.bookings.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CLIENT_BOOKING_HISTORY")
public class ClientBookingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_slug", nullable = false, length = 100)
    private String tenantSlug;

    @Column(name = "salon_name", nullable = false)
    private String salonName;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "care_name", nullable = false)
    private String careName;

    @Column(name = "care_price", nullable = false)
    private Integer carePrice;

    @Column(name = "care_duration", nullable = false)
    private Integer careDuration;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
