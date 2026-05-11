package com.luxpretty.app.bookings.repo;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CareBookingRepository extends JpaRepository<CareBooking, Long> {

    /**
     * Find bookings for a specific date (for slot availability computation)
     */
    List<CareBooking> findByAppointmentDateAndStatusNot(LocalDate date, CareBookingStatus status);

    /**
     * Find bookings for a specific date and employee (for per-employee availability)
     */
    List<CareBooking> findByAppointmentDateAndEmployeeIdAndStatusNot(LocalDate date, Long employeeId, CareBookingStatus status);

    /**
     * Find bookings by status with pagination
     */
    Page<CareBooking> findByStatus(CareBookingStatus status, Pageable pageable);

    /**
     * Find bookings within a date range with pagination
     */
    Page<CareBooking> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Find bookings by status and date range with pagination
     */
    Page<CareBooking> findByStatusAndCreatedAtBetween(
        CareBookingStatus status,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    );

    /**
     * Find bookings by user with pagination
     */
    Page<CareBooking> findByUserId(Long userId, Pageable pageable);

    /**
     * Find future non-cancelled bookings for a specific care
     */
    List<CareBooking> findByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
        Long careId, LocalDate date, CareBookingStatus status);

    /**
     * Find any existing bookings for a specific (date, time, care) triple with a given status.
     * Used on re-booking a previously cancelled slot to hard-delete the stale CANCELLED row
     * before inserting the new one — otherwise the {@code UK_BOOKING_SLOT} unique constraint
     * would fire on the cancelled row.
     */
    List<CareBooking> findByAppointmentDateAndAppointmentTimeAndCareIdAndStatus(
        LocalDate appointmentDate,
        java.time.LocalTime appointmentTime,
        Long careId,
        CareBookingStatus status
    );

    /**
     * Count future non-cancelled bookings for a specific care
     */
    long countByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
        Long careId, LocalDate date, CareBookingStatus status);

    /**
     * Complex query with optional filters using JPQL
     * Eagerly fetch user and care to avoid N+1 queries
     */
    @Query("""
        SELECT DISTINCT b FROM CareBooking b
        LEFT JOIN FETCH b.user
        LEFT JOIN FETCH b.care
        WHERE (:status IS NULL OR b.status = :status)
        AND (:from IS NULL OR b.appointmentDate >= :from)
        AND (:to IS NULL OR b.appointmentDate <= :to)
        AND (:userId IS NULL OR b.user.id = :userId)
        ORDER BY b.appointmentDate ASC, b.appointmentTime ASC
        """)
    Page<CareBooking> findByFilters(
        @Param("status") CareBookingStatus status,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("userId") Long userId,
        Pageable pageable
    );

    /**
     * Same as {@link #findByFilters} but also pins results to a specific
     * {@code employeeId}. Used when the calling user has role EMPLOYEE so they
     * can never see bookings assigned to a colleague.
     */
    @Query("""
        SELECT DISTINCT b FROM CareBooking b
        LEFT JOIN FETCH b.user
        LEFT JOIN FETCH b.care
        WHERE (:status IS NULL OR b.status = :status)
        AND (:from IS NULL OR b.appointmentDate >= :from)
        AND (:to IS NULL OR b.appointmentDate <= :to)
        AND (:userId IS NULL OR b.user.id = :userId)
        AND b.employeeId = :employeeId
        ORDER BY b.appointmentDate ASC, b.appointmentTime ASC
        """)
    Page<CareBooking> findByFiltersAndEmployeeId(
        @Param("status") CareBookingStatus status,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("userId") Long userId,
        @Param("employeeId") Long employeeId,
        Pageable pageable
    );

    /**
     * Find all bookings within a date range (for analytics)
     */
    @Query("""
        SELECT b FROM CareBooking b
        JOIN FETCH b.user
        JOIN FETCH b.care
        WHERE b.appointmentDate BETWEEN :start AND :end
        """)
    List<CareBooking> findByAppointmentDateBetween(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    /**
     * Find future bookings with a specific status (for forecast)
     */
    @Query("""
        SELECT b FROM CareBooking b
        JOIN FETCH b.care
        WHERE b.appointmentDate >= :date AND b.status = :status
        """)
    List<CareBooking> findByAppointmentDateGreaterThanEqualAndStatus(
        @Param("date") LocalDate date,
        @Param("status") CareBookingStatus status
    );

    /**
     * Count "live" bookings (PENDING/CONFIRMED) for a user on a given day.
     * Used by the daily-limit check in BookingPolicyService.
     */
    long countByUserIdAndAppointmentDateAndStatusIn(
        Long userId,
        LocalDate date,
        Collection<CareBookingStatus> statuses
    );

    /**
     * Count "live" bookings (PENDING/CONFIRMED) for a user inside a date range.
     * Used by the new-client weekly-limit check.
     */
    long countByUserIdAndAppointmentDateBetweenAndStatusIn(
        Long userId,
        LocalDate from,
        LocalDate to,
        Collection<CareBookingStatus> statuses
    );

    /**
     * True if the user has at least one CONFIRMED booking strictly in the past.
     * Used to identify whether a client is "new" to this salon (false → new).
     */
    boolean existsByUserIdAndStatusAndAppointmentDateBefore(
        Long userId,
        CareBookingStatus status,
        LocalDate today
    );
}

