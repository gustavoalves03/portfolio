package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.repo.BookingPolicyRepository;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.common.error.BookingLimitExceededException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enforces per-tenant client booking limits before a CareBooking is persisted.
 * The policy is a singleton row in BOOKING_POLICY (per-tenant schema). If the
 * row is missing for a legacy tenant, default values (1/1) are auto-created on
 * first read.
 */
@Service
public class BookingPolicyService {

    private static final List<CareBookingStatus> LIVE_STATUSES = List.of(
            CareBookingStatus.PENDING, CareBookingStatus.CONFIRMED);

    private static final int DEFAULT_MAX_PER_DAY = 1;
    private static final int DEFAULT_MAX_PER_WEEK_NEW_CLIENT = 1;

    private final BookingPolicyRepository policyRepo;
    private final CareBookingRepository bookingRepo;

    public BookingPolicyService(BookingPolicyRepository policyRepo, CareBookingRepository bookingRepo) {
        this.policyRepo = policyRepo;
        this.bookingRepo = bookingRepo;
    }

    @Transactional
    public BookingPolicy getOrCreatePolicy() {
        return policyRepo.findFirstByOrderByIdAsc().orElseGet(() -> {
            BookingPolicy fresh = new BookingPolicy();
            fresh.setMaxBookingsPerDayPerClient(DEFAULT_MAX_PER_DAY);
            fresh.setMaxBookingsPerWeekForNewClient(DEFAULT_MAX_PER_WEEK_NEW_CLIENT);
            fresh.setUpdatedAt(LocalDateTime.now());
            return policyRepo.save(fresh);
        });
    }

    @Transactional
    public BookingPolicy update(int maxPerDay, int maxPerWeekNewClient) {
        BookingPolicy current = getOrCreatePolicy();
        current.setMaxBookingsPerDayPerClient(maxPerDay);
        current.setMaxBookingsPerWeekForNewClient(maxPerWeekNewClient);
        current.setUpdatedAt(LocalDateTime.now());
        return policyRepo.save(current);
    }

    /**
     * Throws BookingLimitExceededException if either daily or weekly-for-new-client
     * limit is breached. Caller is expected to invoke this BEFORE saving the new
     * CareBooking, inside the same transaction as the booking insert.
     */
    @Transactional(readOnly = true)
    public void validateClientLimits(Long userId, LocalDate appointmentDate) {
        BookingPolicy policy = getOrCreatePolicy();

        long dailyCount = bookingRepo.countByUserIdAndAppointmentDateAndStatusIn(
                userId, appointmentDate, LIVE_STATUSES);
        if (dailyCount >= policy.getMaxBookingsPerDayPerClient()) {
            throw new BookingLimitExceededException(
                    BookingLimitExceededException.CODE_DAILY,
                    policy.getMaxBookingsPerDayPerClient(),
                    (int) dailyCount);
        }

        boolean isExistingClient = bookingRepo.existsByUserIdAndStatusAndAppointmentDateBefore(
                userId, CareBookingStatus.CONFIRMED, LocalDate.now());
        if (!isExistingClient) {
            LocalDate weekStart = appointmentDate.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            long weeklyCount = bookingRepo.countByUserIdAndAppointmentDateBetweenAndStatusIn(
                    userId, weekStart, weekEnd, LIVE_STATUSES);
            if (weeklyCount >= policy.getMaxBookingsPerWeekForNewClient()) {
                throw new BookingLimitExceededException(
                        BookingLimitExceededException.CODE_NEW_CLIENT_WEEKLY,
                        policy.getMaxBookingsPerWeekForNewClient(),
                        (int) weeklyCount);
            }
        }
    }
}
