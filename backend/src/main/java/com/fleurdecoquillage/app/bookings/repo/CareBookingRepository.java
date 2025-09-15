package com.fleurdecoquillage.app.bookings.repo;

import com.fleurdecoquillage.app.bookings.domain.CareBooking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareBookingRepository extends JpaRepository<CareBooking, Long> {
}

