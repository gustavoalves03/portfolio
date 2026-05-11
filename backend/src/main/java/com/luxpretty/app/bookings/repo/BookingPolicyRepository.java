package com.luxpretty.app.bookings.repo;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingPolicyRepository extends JpaRepository<BookingPolicy, Long> {

    /**
     * Singleton-per-tenant: returns the single BookingPolicy row of the current
     * tenant schema, or empty if the row does not exist yet.
     */
    Optional<BookingPolicy> findFirstByOrderByIdAsc();
}
