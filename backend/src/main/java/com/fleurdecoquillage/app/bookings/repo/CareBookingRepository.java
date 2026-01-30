package com.fleurdecoquillage.app.bookings.repo;

import com.fleurdecoquillage.app.bookings.domain.CareBooking;
import com.fleurdecoquillage.app.bookings.domain.CareBookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CareBookingRepository extends JpaRepository<CareBooking, Long> {

    /**
     * Find bookings by status with pagination
     */
    Page<CareBooking> findByStatus(CareBookingStatus status, Pageable pageable);

    /**
     * Find bookings within a date range with pagination
     */
    Page<CareBooking> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    /**
     * Find bookings by status and date range with pagination
     */
    Page<CareBooking> findByStatusAndCreatedAtBetween(
        CareBookingStatus status,
        Instant from,
        Instant to,
        Pageable pageable
    );

    /**
     * Find bookings by user with pagination
     */
    Page<CareBooking> findByUserId(Long userId, Pageable pageable);

    /**
     * Complex query with optional filters using JPQL
     * Eagerly fetch user and care to avoid N+1 queries
     */
    @Query("""
        SELECT DISTINCT b FROM CareBooking b
        LEFT JOIN FETCH b.user
        LEFT JOIN FETCH b.care
        WHERE (:status IS NULL OR b.status = :status)
        AND (:from IS NULL OR b.createdAt >= :from)
        AND (:to IS NULL OR b.createdAt <= :to)
        AND (:userId IS NULL OR b.user.id = :userId)
        ORDER BY b.createdAt DESC
        """)
    Page<CareBooking> findByFilters(
        @Param("status") CareBookingStatus status,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("userId") Long userId,
        Pageable pageable
    );
}

