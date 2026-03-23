package com.fleurdecoquillage.app.availability.app;

import com.fleurdecoquillage.app.availability.domain.BlockedSlot;
import com.fleurdecoquillage.app.availability.domain.OpeningHour;
import com.fleurdecoquillage.app.availability.repo.BlockedSlotRepository;
import com.fleurdecoquillage.app.availability.repo.OpeningHourRepository;
import com.fleurdecoquillage.app.bookings.domain.CareBooking;
import com.fleurdecoquillage.app.bookings.domain.CareBookingStatus;
import com.fleurdecoquillage.app.bookings.repo.CareBookingRepository;
import com.fleurdecoquillage.app.care.domain.Care;
import com.fleurdecoquillage.app.care.repo.CareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlotAvailabilityService {

    private static final int SLOT_INTERVAL_MINUTES = 30;

    private final OpeningHourRepository openingHourRepo;
    private final BlockedSlotRepository blockedSlotRepo;
    private final CareBookingRepository bookingRepo;
    private final CareRepository careRepo;

    public SlotAvailabilityService(OpeningHourRepository openingHourRepo,
                                   BlockedSlotRepository blockedSlotRepo,
                                   CareBookingRepository bookingRepo,
                                   CareRepository careRepo) {
        this.openingHourRepo = openingHourRepo;
        this.blockedSlotRepo = blockedSlotRepo;
        this.bookingRepo = bookingRepo;
        this.careRepo = careRepo;
    }

    /**
     * Compute available time slots for a given date and care service.
     * Takes into account opening hours, blocked slots, and existing bookings.
     */
    @Transactional(readOnly = true)
    public List<TimeSlot> getAvailableSlots(LocalDate date, Long careId) {
        if (date.isBefore(LocalDate.now())) {
            return List.of();
        }

        Care care = careRepo.findById(careId)
                .orElseThrow(() -> new IllegalArgumentException("Care not found: " + careId));
        int durationMinutes = care.getDuration();

        // Get day of week (Monday=1..Sunday=7)
        int dow = date.getDayOfWeek().getValue();

        // Get opening hours for this day
        List<OpeningHour> openingHours = openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()
                .stream()
                .filter(oh -> oh.getDayOfWeek() == dow)
                .toList();

        if (openingHours.isEmpty()) {
            return List.of(); // Closed day
        }

        // Get blocked slots for this date
        List<BlockedSlot> blockedSlots = blockedSlotRepo
                .findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(date)
                .stream()
                .filter(bs -> bs.getDate().equals(date))
                .toList();

        // Check if full day is blocked
        boolean fullDayBlocked = blockedSlots.stream().anyMatch(BlockedSlot::isFullDay);
        if (fullDayBlocked) {
            return List.of();
        }

        // Get existing bookings for this date (exclude cancelled)
        List<CareBooking> existingBookings = bookingRepo
                .findByAppointmentDateAndStatusNot(date, CareBookingStatus.CANCELLED);

        // Generate candidate slots from opening hours
        List<TimeSlot> availableSlots = new ArrayList<>();

        for (OpeningHour oh : openingHours) {
            LocalTime cursor = oh.getOpenTime();
            LocalTime windowEnd = oh.getCloseTime();

            while (cursor.plusMinutes(durationMinutes).compareTo(windowEnd) <= 0) {
                LocalTime slotEnd = cursor.plusMinutes(durationMinutes);

                if (!isBlockedAt(cursor, slotEnd, blockedSlots)
                        && !isBookedAt(cursor, slotEnd, existingBookings)) {
                    availableSlots.add(new TimeSlot(cursor.toString(), slotEnd.toString()));
                }

                cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
            }
        }

        return availableSlots;
    }

    private boolean isBlockedAt(LocalTime start, LocalTime end, List<BlockedSlot> blockedSlots) {
        for (BlockedSlot bs : blockedSlots) {
            if (bs.isFullDay()) return true;
            if (bs.getStartTime() != null && bs.getEndTime() != null) {
                // Overlap check
                if (start.isBefore(bs.getEndTime()) && end.isAfter(bs.getStartTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBookedAt(LocalTime start, LocalTime end, List<CareBooking> bookings) {
        for (CareBooking booking : bookings) {
            LocalTime bookingStart = booking.getAppointmentTime();
            int bookingDuration = booking.getCare().getDuration();
            LocalTime bookingEnd = bookingStart.plusMinutes(bookingDuration);

            // Overlap check
            if (start.isBefore(bookingEnd) && end.isAfter(bookingStart)) {
                return true;
            }
        }
        return false;
    }

    public record TimeSlot(String startTime, String endTime) {}
}
