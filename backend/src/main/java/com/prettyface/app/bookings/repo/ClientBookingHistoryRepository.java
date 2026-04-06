package com.prettyface.app.bookings.repo;

import com.prettyface.app.bookings.domain.ClientBookingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientBookingHistoryRepository extends JpaRepository<ClientBookingHistory, Long> {

    List<ClientBookingHistory> findByUserIdAndStatusAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscAppointmentTimeAsc(
            Long userId, String status, LocalDate fromDate);

    List<ClientBookingHistory> findByUserIdAndAppointmentDateBeforeOrderByAppointmentDateDescAppointmentTimeDesc(
            Long userId, LocalDate beforeDate);

    Optional<ClientBookingHistory> findByTenantSlugAndBookingId(String tenantSlug, Long bookingId);

    List<ClientBookingHistory> findByUserIdAndAppointmentDateAndStatusNot(
            Long userId, LocalDate date, String status);
}
