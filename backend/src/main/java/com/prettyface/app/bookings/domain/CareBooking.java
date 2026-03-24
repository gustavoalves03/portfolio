package com.prettyface.app.bookings.domain;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.users.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "CARE_BOOKINGS", uniqueConstraints = {
        @UniqueConstraint(
                name = "UK_BOOKING_SLOT",
                columnNames = {"appointment_date", "appointment_time", "care_id"}
        )
})
public class CareBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "FK_BOOKING_USER"))
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "care_id", nullable = false,
                foreignKey = @ForeignKey(name = "FK_BOOKING_CARE"))
    private Care care;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CareBookingStatus status = CareBookingStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

