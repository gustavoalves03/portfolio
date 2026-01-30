package com.fleurdecoquillage.app.bookings.domain;

import com.fleurdecoquillage.app.care.domain.Care;
import com.fleurdecoquillage.app.users.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "CARE_BOOKINGS")
public class CareBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "care_id", nullable = false)
    private Care care;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime appointmentTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareBookingStatus status = CareBookingStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}

