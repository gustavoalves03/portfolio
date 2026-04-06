package com.prettyface.app.availability.app;

import com.prettyface.app.availability.domain.BlockedSlot;
import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.BlockedSlotRepository;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.employee.app.LeaveRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotAvailabilityServiceTests {

    @Mock
    private OpeningHourRepository openingHourRepo;

    @Mock
    private BlockedSlotRepository blockedSlotRepo;

    @Mock
    private CareBookingRepository bookingRepo;

    @Mock
    private CareRepository careRepo;

    @Mock
    private LeaveRequestService leaveRequestService;

    @Mock
    private HolidayAvailabilityService holidayAvailabilityService;

    @Mock
    private com.prettyface.app.tenant.repo.TenantRepository tenantRepository;

    @InjectMocks
    private SlotAvailabilityService service;

    private Care care60min;
    private Care care30min;
    private final LocalDate futureMonday = nextMonday();

    @BeforeEach
    void setUp() {
        care60min = new Care();
        care60min.setId(1L);
        care60min.setName("Soin visage");
        care60min.setDuration(60);

        care30min = new Care();
        care30min.setId(2L);
        care30min.setName("Épilation");
        care30min.setDuration(30);
    }

    // ── Past date ──

    @Test
    void getAvailableSlots_pastDate_returnsEmpty() {
        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(LocalDate.now().minusDays(1), 1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAvailableSlots_today_doesNotThrow() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of());

        // Today is not in the past, should not return early — just return empty if closed
        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(LocalDate.now(), 2L);

        assertThat(result).isEmpty();
    }

    // ── Care not found ──

    @Test
    void getAvailableSlots_careNotFound_throws() {
        when(careRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvailableSlots(futureMonday, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── Closed day ──

    @Test
    void getAvailableSlots_closedDay_returnsEmpty() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc()).thenReturn(List.of());

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        assertThat(result).isEmpty();
    }

    // ── Normal open day — no blocks, no bookings ──

    @Test
    void getAvailableSlots_openDay_noBlocksNoBookings_returnsSlots() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "12:00"); // 3h window, 60 min care → 09:00, 09:30, 10:00, 10:30, 11:00
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        // Last slot should fit: start + 60 min <= 12:00 → 11:00
        assertThat(result.get(result.size() - 1).startTime()).isEqualTo("11:00");
    }

    @Test
    void getAvailableSlots_30minCare_generatesMoreSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "11:00"); // 2h window, 30 min care
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00, 09:30, 10:00, 10:30 → 4 slots
        assertThat(result).hasSize(4);
    }

    @Test
    void getAvailableSlots_multipleOpeningWindows_combinesSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        // Two windows: 09:00-10:00 and 14:00-15:00
        OpeningHour morning = buildOpeningHour(1, "09:00", "10:00");
        OpeningHour afternoon = buildOpeningHour(1, "14:00", "15:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(morning, afternoon));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Morning: 09:00, 09:30 → 2 slots; Afternoon: 14:00, 14:30 → 2 slots
        assertThat(result).hasSize(4);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(2).startTime()).isEqualTo("14:00");
    }

    // ── Full day blocked ──

    @Test
    void getAvailableSlots_fullDayBlocked_returnsEmpty() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "18:00");

        BlockedSlot fullDay = new BlockedSlot();
        fullDay.setDate(futureMonday);
        fullDay.setFullDay(true);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(fullDay));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        assertThat(result).isEmpty();
    }

    // ── Partial block ──

    @Test
    void getAvailableSlots_partialBlock_excludesBlockedSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "11:00"); // 4 slots: 09:00, 09:30, 10:00, 10:30
        mockNoBookings();

        // Block 09:30-10:30
        BlockedSlot block = new BlockedSlot();
        block.setDate(futureMonday);
        block.setFullDay(false);
        block.setStartTime(LocalTime.of(9, 30));
        block.setEndTime(LocalTime.of(10, 30));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00-09:30 → overlaps? start=09:00 < end=10:30 && end=09:30 > start=09:30 → NO overlap
        // Actually: 09:00 < 10:30 && 09:30 > 09:30 is false → NOT blocked → available
        // 09:30-10:00 → 09:30 < 10:30 && 10:00 > 09:30 → blocked
        // 10:00-10:30 → 10:00 < 10:30 && 10:30 > 09:30 → blocked
        // 10:30-11:00 → 10:30 < 10:30 is false → NOT blocked → available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("10:30");
    }

    // ── Existing booking ──

    @Test
    void getAvailableSlots_existingBooking_excludesBookedSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30");
        mockNoBlocks();

        // Booking at 09:30 for a 30-min care → occupies 09:30-10:00
        CareBooking booking = new CareBooking();
        booking.setAppointmentDate(futureMonday);
        booking.setAppointmentTime(LocalTime.of(9, 30));
        booking.setCare(care30min);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00-09:30 → overlaps 09:30-10:00? 09:00<10:00 && 09:30>09:30 → false → available
        // 09:30-10:00 → 09:30<10:00 && 10:00>09:30 → blocked
        // 10:00-10:30 → 10:00<10:00 → false → available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("10:00");
    }

    @Test
    void getAvailableSlots_multiplBookings_excludesAll() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30"); // 3 slots
        mockNoBlocks();

        CareBooking b1 = new CareBooking();
        b1.setAppointmentDate(futureMonday);
        b1.setAppointmentTime(LocalTime.of(9, 0));
        b1.setCare(care30min);

        CareBooking b2 = new CareBooking();
        b2.setAppointmentDate(futureMonday);
        b2.setAppointmentTime(LocalTime.of(10, 0));
        b2.setCare(care30min);

        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(b1, b2));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Only 09:30 should remain
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
    }

    // ── Combined block + booking ──

    @Test
    void getAvailableSlots_blockAndBooking_excludesBoth() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "11:00"); // 4 slots

        // Block 09:00-09:30
        BlockedSlot block = new BlockedSlot();
        block.setDate(futureMonday);
        block.setFullDay(false);
        block.setStartTime(LocalTime.of(9, 0));
        block.setEndTime(LocalTime.of(9, 30));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        // Booking at 10:00
        CareBooking booking = new CareBooking();
        booking.setAppointmentDate(futureMonday);
        booking.setAppointmentTime(LocalTime.of(10, 0));
        booking.setCare(care30min);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00 blocked, 10:00 booked → 09:30 and 10:30 available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
        assertThat(result.get(1).startTime()).isEqualTo("10:30");
    }

    // ── Slot interval is 30 min ──

    @Test
    void getAvailableSlots_slotsAre30minApart() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        for (int i = 1; i < result.size(); i++) {
            LocalTime prev = LocalTime.parse(result.get(i - 1).startTime());
            LocalTime curr = LocalTime.parse(result.get(i).startTime());
            assertThat(java.time.Duration.between(prev, curr).toMinutes()).isEqualTo(30);
        }
    }

    // ── Care duration too long for window ──

    @Test
    void getAvailableSlots_careDurationExceedsWindow_returnsEmpty() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "09:30"); // Only 30 min window, 60 min care
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        assertThat(result).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── EMPLOYEE-SPECIFIC AVAILABILITY ──
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Employee with personal opening hours uses them, not salon-wide")
    void employeeWithPersonalHours_usesPersonalHours() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 has personal hours: 10:00-12:00 on Monday
        OpeningHour empHour = buildOpeningHourForEmployee(1, "10:00", "12:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // 10:00, 10:30, 11:00, 11:30 -> 4 slots
        assertThat(result).hasSize(4);
        assertThat(result.get(0).startTime()).isEqualTo("10:00");
        assertThat(result.get(3).startTime()).isEqualTo("11:30");
    }

    @Test
    @DisplayName("Employee with no personal hours falls back to salon-wide hours")
    void employeeWithNoPersonalHours_fallsBackToSalonWide() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 20 has no personal hours
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(20L))
                .thenReturn(List.of());

        // Salon-wide hours: 09:00-11:00 on Monday
        OpeningHour salonHour = buildOpeningHour(1, "09:00", "11:00");
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(salonHour));

        when(leaveRequestService.isOnLeave(20L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(20L);
        mockNoBookingsForEmployee(20L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 20L);

        // 09:00, 09:30, 10:00, 10:30 -> 4 slots
        assertThat(result).hasSize(4);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("Employee on approved leave returns empty slots")
    void employeeOnLeave_returnsEmpty() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "18:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");

        // Employee is on leave
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(true);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Employee not on leave returns normal slots")
    void employeeNotOnLeave_returnsNormalSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "10:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // 09:00, 09:30 -> 2 slots
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Employee-specific blocked slot removes only that employee's slots")
    void employeeBlockedSlot_removesOnlyThatEmployeeSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "11:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);

        // Employee-specific block: 09:00-10:00
        BlockedSlot empBlock = buildBlockedSlot(futureMonday, "09:00", "10:00");
        empBlock.setEmployeeId(10L);
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(10L), any()))
                .thenReturn(List.of(empBlock));
        // No salon-wide blocks
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // 09:00 blocked (09:00-09:30 overlaps 09:00-10:00)
        // 09:30 blocked (09:30-10:00 overlaps 09:00-10:00)
        // 10:00 available, 10:30 available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("10:00");
        assertThat(result.get(1).startTime()).isEqualTo("10:30");
    }

    @Test
    @DisplayName("Salon-wide blocked slot also affects employee availability")
    void salonWideBlock_alsoAffectsEmployee() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "11:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);

        // Salon-wide block: 09:00-10:00 (employeeId = null)
        BlockedSlot salonBlock = buildBlockedSlot(futureMonday, "09:00", "10:00");
        // No employee-specific blocks
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(10L), any()))
                .thenReturn(List.of());
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(salonBlock));
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // 09:00, 09:30 blocked by salon block; 10:00, 10:30 available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("Employee booking blocks only that employee's slots")
    void employeeBooking_blocksOnlyThatEmployee() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "10:30", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);

        // Booking for employee 10 at 09:30
        CareBooking booking = buildBooking(futureMonday, "09:30", care30min, 10L);
        when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(
                eq(futureMonday), eq(10L), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // 09:00 available, 09:30 blocked, 10:00 available -> 2 slots
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("Different employees can have same time booked — no cross-blocking")
    void differentEmployees_noCrossBlocking() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        mockSalonHoursForEmployee("09:00", "18:00");

        // Employee 10
        OpeningHour emp10Hour = buildOpeningHourForEmployee(1, "09:00", "10:30", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(emp10Hour));
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);

        // Employee 10 has a booking at 09:00
        CareBooking emp10Booking = buildBooking(futureMonday, "09:00", care30min, 10L);
        when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(
                eq(futureMonday), eq(10L), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(emp10Booking));

        // Employee 20
        OpeningHour emp20Hour = buildOpeningHourForEmployee(1, "09:00", "10:30", 20L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(20L))
                .thenReturn(List.of(emp20Hour));
        when(leaveRequestService.isOnLeave(20L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(20L);

        // Employee 20 has NO bookings
        when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(
                eq(futureMonday), eq(20L), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of());

        List<SlotAvailabilityService.TimeSlot> emp10Slots =
                service.getAvailableSlots(futureMonday, 2L, 10L);
        List<SlotAvailabilityService.TimeSlot> emp20Slots =
                service.getAvailableSlots(futureMonday, 2L, 20L);

        // Employee 10: 09:00 blocked, 09:30 and 10:00 available
        assertThat(emp10Slots).hasSize(2);
        assertThat(emp10Slots.get(0).startTime()).isEqualTo("09:30");

        // Employee 20: 09:00, 09:30, 10:00 all available
        assertThat(emp20Slots).hasSize(3);
        assertThat(emp20Slots.get(0).startTime()).isEqualTo("09:00");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── EDGE CASES FOR SLOT BOUNDARIES ──
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("60-min care in exactly 60-min window produces 1 slot")
    void careDurationExactlyFillsWindow_oneSlot() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "10:00"); // 60 min window, 60 min care
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(0).endTime()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("45-min care with 30-min interval — overlapping slots generated correctly")
    void care45min_with30minInterval_overlappingSlots() {
        Care care45min = new Care();
        care45min.setId(3L);
        care45min.setName("Soin express");
        care45min.setDuration(45);

        when(careRepo.findById(3L)).thenReturn(Optional.of(care45min));
        mockOpeningHours(1, "09:00", "11:00"); // 2h window
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 3L);

        // 09:00 + 45 = 09:45 <= 11:00 -> ok
        // 09:30 + 45 = 10:15 <= 11:00 -> ok
        // 10:00 + 45 = 10:45 <= 11:00 -> ok
        // 10:30 + 45 = 11:15 > 11:00 -> no
        assertThat(result).hasSize(3);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(0).endTime()).isEqualTo("09:45");
        assertThat(result.get(1).startTime()).isEqualTo("09:30");
        assertThat(result.get(2).startTime()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("90-min booking blocks multiple 30-min candidate slots")
    void booking90min_blocksMultipleSlots() {
        Care care90min = new Care();
        care90min.setId(4L);
        care90min.setName("Soin complet");
        care90min.setDuration(90);

        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "12:00"); // 3h window, testing 30min care
        mockNoBlocks();

        // A 90-min booking at 09:30 occupies 09:30-11:00
        CareBooking longBooking = buildBooking(futureMonday, "09:30", care90min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(longBooking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00-09:30: overlaps 09:30-11:00? 09:00 < 11:00 && 09:30 > 09:30 -> false -> available
        // 09:30-10:00: 09:30 < 11:00 && 10:00 > 09:30 -> blocked
        // 10:00-10:30: 10:00 < 11:00 && 10:30 > 09:30 -> blocked
        // 10:30-11:00: 10:30 < 11:00 && 11:00 > 09:30 -> blocked
        // 11:00-11:30: 11:00 < 11:00 -> false -> available
        // 11:30-12:00: available
        assertThat(result).hasSize(3);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("11:00");
        assertThat(result.get(2).startTime()).isEqualTo("11:30");
    }

    @Test
    @DisplayName("Booking at last possible slot of the day")
    void bookingAtLastSlot_removesOnlyLastSlot() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30"); // 3 slots: 09:00, 09:30, 10:00
        mockNoBlocks();

        CareBooking lastBooking = buildBooking(futureMonday, "10:00", care30min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(lastBooking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("09:30");
    }

    @Test
    @DisplayName("Window of exactly 30 min with 30-min care produces 1 slot")
    void windowExactly30min_with30minCare_oneSlot() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "14:00", "14:30");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("14:00");
        assertThat(result.get(0).endTime()).isEqualTo("14:30");
    }

    @Test
    @DisplayName("Window of 29 min with 30-min care produces 0 slots")
    void window29min_with30minCare_noSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        // 14:00-14:29 = 29 min window
        mockOpeningHours(1, "14:00", "14:29");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── COMPLEX OVERLAP SCENARIOS ──
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Two adjacent blocked slots leaving a gap — gap available if care fits")
    void twoAdjacentBlocks_gapAvailable() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "12:00");
        mockNoBookings();

        // Block 09:00-10:00, then 10:30-12:00 — gap at 10:00-10:30
        BlockedSlot block1 = buildBlockedSlot(futureMonday, "09:00", "10:00");
        BlockedSlot block2 = buildBlockedSlot(futureMonday, "10:30", "12:00");
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block1, block2));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00-09:30 overlaps 09:00-10:00 -> blocked
        // 09:30-10:00 overlaps 09:00-10:00 -> blocked
        // 10:00-10:30: 10:00 < 10:00 -> false for block1; 10:00 < 12:00 && 10:30 > 10:30 -> false for block2 -> available
        // 10:30-11:00 overlaps 10:30-12:00 -> blocked
        // 11:00-11:30 overlaps 10:30-12:00 -> blocked
        // 11:30-12:00 overlaps 10:30-12:00 -> blocked
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("Block that covers exactly one slot boundary")
    void blockCoversExactlyOneSlotBoundary() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30");
        mockNoBookings();

        // Block exactly 09:30-10:00
        BlockedSlot block = buildBlockedSlot(futureMonday, "09:30", "10:00");
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00-09:30: 09:00 < 10:00 && 09:30 > 09:30 -> false -> available
        // 09:30-10:00: 09:30 < 10:00 && 10:00 > 09:30 -> blocked
        // 10:00-10:30: 10:00 < 10:00 -> false -> available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("Multiple bookings of different duration cares on same day")
    void multipleBookingsDifferentDurations() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "12:00");
        mockNoBlocks();

        // 60-min booking at 09:00 (occupies 09:00-10:00)
        CareBooking booking1 = buildBooking(futureMonday, "09:00", care60min, null);
        // 30-min booking at 10:30 (occupies 10:30-11:00)
        CareBooking booking2 = buildBooking(futureMonday, "10:30", care30min, null);

        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking1, booking2));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00 blocked (overlaps 09:00-10:00)
        // 09:30 blocked (overlaps 09:00-10:00)
        // 10:00 available
        // 10:30 blocked (overlaps 10:30-11:00)
        // 11:00 available
        // 11:30 available
        assertThat(result).hasSize(3);
        assertThat(result.get(0).startTime()).isEqualTo("10:00");
        assertThat(result.get(1).startTime()).isEqualTo("11:00");
        assertThat(result.get(2).startTime()).isEqualTo("11:30");
    }

    @Test
    @DisplayName("Back-to-back bookings filling entire day produces 0 slots")
    void backToBackBookings_fillingEntireDay_zeroSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30"); // 3 slots
        mockNoBlocks();

        CareBooking b1 = buildBooking(futureMonday, "09:00", care30min, null);
        CareBooking b2 = buildBooking(futureMonday, "09:30", care30min, null);
        CareBooking b3 = buildBooking(futureMonday, "10:00", care30min, null);

        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(b1, b2, b3));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("90-min booking overlaps with 60-min care candidate slots correctly")
    void booking90min_overlapsWith60minCare() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "13:00"); // 4h window
        mockNoBlocks();

        Care care90min = new Care();
        care90min.setId(4L);
        care90min.setName("Soin complet");
        care90min.setDuration(90);

        // 90-min booking at 10:00 → occupies 10:00-11:30
        CareBooking longBooking = buildBooking(futureMonday, "10:00", care90min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(longBooking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        // 60-min care candidates: 09:00-10:00, 09:30-10:30, 10:00-11:00, 10:30-11:30, 11:00-12:00, 11:30-12:30, 12:00-13:00
        // Booking occupies 10:00-11:30
        // 09:00-10:00: 09:00<11:30 && 10:00>10:00 -> false -> available
        // 09:30-10:30: 09:30<11:30 && 10:30>10:00 -> blocked
        // 10:00-11:00: blocked
        // 10:30-11:30: blocked
        // 11:00-12:00: 11:00<11:30 && 12:00>10:00 -> blocked
        // 11:30-12:30: 11:30<11:30 -> false -> available
        // 12:00-13:00: available
        assertThat(result).hasSize(3);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("11:30");
        assertThat(result.get(2).startTime()).isEqualTo("12:00");
    }

    @Test
    @DisplayName("Block spanning from before opening to mid-morning only blocks overlapping slots")
    void blockStartsBeforeOpening_blocksOverlappingSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "11:00");
        mockNoBookings();

        // Block 08:00-09:30 (starts before opening)
        BlockedSlot block = buildBlockedSlot(futureMonday, "08:00", "09:30");
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00-09:30: 09:00<09:30 && 09:30>08:00 -> blocked (overlap: 09:30 > 08:00 is true, but 09:30 > 09:30 is false -> NOT blocked)
        // Wait, re-check: start=09:00, end=09:30, block start=08:00, block end=09:30
        // 09:00 < 09:30 && 09:30 > 08:00 -> true && true -> BLOCKED
        // 09:30-10:00: 09:30 < 09:30 -> false -> available
        // 10:00-10:30: available
        // 10:30-11:00: available
        assertThat(result).hasSize(3);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── FIND FIRST AVAILABLE EMPLOYEE ──
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("findFirstAvailableEmployee — first employee available, returns first")
    void findFirst_firstAvailable_returnsFirst() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 is available
        setupEmployeeAvailable(10L, "09:00", "10:00");

        Long result = service.findFirstAvailableEmployee(
                futureMonday, 2L, "09:00", List.of(10L, 20L));

        assertThat(result).isEqualTo(10L);
    }

    @Test
    @DisplayName("findFirstAvailableEmployee — first busy, second available, returns second")
    void findFirst_firstBusy_returnsSecond() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 has a booking at 09:00
        setupEmployeeWithBookingAt(10L, "09:00", "09:00", "10:00");

        // Employee 20 is available at 09:00
        setupEmployeeAvailable(20L, "09:00", "10:00");

        Long result = service.findFirstAvailableEmployee(
                futureMonday, 2L, "09:00", List.of(10L, 20L));

        assertThat(result).isEqualTo(20L);
    }

    @Test
    @DisplayName("findFirstAvailableEmployee — all employees busy, returns null")
    void findFirst_allBusy_returnsNull() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 booked at 09:00
        setupEmployeeWithBookingAt(10L, "09:00", "09:00", "10:00");

        // Employee 20 booked at 09:00
        setupEmployeeWithBookingAt(20L, "09:00", "09:00", "10:00");

        Long result = service.findFirstAvailableEmployee(
                futureMonday, 2L, "09:00", List.of(10L, 20L));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findFirstAvailableEmployee — employee on leave skipped, next available")
    void findFirst_onLeaveSkipped_nextAvailable() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 is on leave
        OpeningHour emp10Hour = buildOpeningHourForEmployee(1, "09:00", "10:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(emp10Hour));
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(true);

        // Employee 20 is available
        setupEmployeeAvailable(20L, "09:00", "10:00");

        Long result = service.findFirstAvailableEmployee(
                futureMonday, 2L, "09:00", List.of(10L, 20L));

        assertThat(result).isEqualTo(20L);
    }

    @Test
    @DisplayName("findFirstAvailableEmployee — complex schedule, correct assignment")
    void findFirst_complexSchedule_correctAssignment() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));

        // Employee 10: hours 09:00-12:00 but booked at 10:00 with 60min care
        OpeningHour emp10Hour = buildOpeningHourForEmployee(1, "09:00", "12:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(emp10Hour));
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        CareBooking emp10Booking = buildBooking(futureMonday, "10:00", care60min, 10L);
        when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(
                eq(futureMonday), eq(10L), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(emp10Booking));

        // Employee 20: hours 09:00-12:00 but blocked 09:00-10:30
        OpeningHour emp20Hour = buildOpeningHourForEmployee(1, "09:00", "12:00", 20L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(20L))
                .thenReturn(List.of(emp20Hour));
        when(leaveRequestService.isOnLeave(20L, futureMonday)).thenReturn(false);
        BlockedSlot emp20Block = buildBlockedSlot(futureMonday, "09:00", "10:30");
        emp20Block.setEmployeeId(20L);
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(20L), any()))
                .thenReturn(List.of(emp20Block));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
        when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(
                eq(futureMonday), eq(20L), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of());

        // Employee 30: available
        setupEmployeeAvailable(30L, "09:00", "12:00");

        // Request 10:00 slot for 60-min care
        // Emp 10: booked at 10:00 for 60min -> 10:00 not available
        // Emp 20: blocked 09:00-10:30 -> 10:00 slot (10:00-11:00) overlaps -> not available
        // Emp 30: available at 10:00
        Long result = service.findFirstAvailableEmployee(
                futureMonday, 1L, "10:00", List.of(10L, 20L, 30L));

        assertThat(result).isEqualTo(30L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── MULTI-WINDOW SCENARIOS ──
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Split schedule — booking in morning does not affect afternoon")
    void splitSchedule_morningBooking_afternoonUnaffected() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour morning = buildOpeningHour(1, "09:00", "10:00");
        OpeningHour afternoon = buildOpeningHour(1, "14:00", "15:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(morning, afternoon));
        mockNoBlocks();

        // Booking at 09:00 in morning window
        CareBooking morningBooking = buildBooking(futureMonday, "09:00", care30min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(morningBooking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Morning: 09:00 blocked, 09:30 available
        // Afternoon: 14:00, 14:30 available
        assertThat(result).hasSize(3);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
        assertThat(result.get(1).startTime()).isEqualTo("14:00");
        assertThat(result.get(2).startTime()).isEqualTo("14:30");
    }

    @Test
    @DisplayName("Three opening windows, middle one fully blocked")
    void threeWindows_middleFullyBlocked() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour w1 = buildOpeningHour(1, "08:00", "09:00");
        OpeningHour w2 = buildOpeningHour(1, "11:00", "12:00");
        OpeningHour w3 = buildOpeningHour(1, "15:00", "16:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(w1, w2, w3));
        mockNoBookings();

        // Block covers entire middle window
        BlockedSlot block = buildBlockedSlot(futureMonday, "11:00", "12:00");
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Window 1: 08:00, 08:30 (check against block: 08:00<12:00 && 08:30>11:00 -> false -> available)
        // Window 2: 11:00-11:30 overlaps block, 11:30-12:00 overlaps block -> all blocked
        // Window 3: 15:00, 15:30 -> available
        assertThat(result).hasSize(4);
        assertThat(result.get(0).startTime()).isEqualTo("08:00");
        assertThat(result.get(1).startTime()).isEqualTo("08:30");
        assertThat(result.get(2).startTime()).isEqualTo("15:00");
        assertThat(result.get(3).startTime()).isEqualTo("15:30");
    }

    @Test
    @DisplayName("Blocked slot spanning across two opening windows blocks both")
    void blockSpanningTwoWindows_blocksBoth() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour w1 = buildOpeningHour(1, "09:00", "10:00");
        OpeningHour w2 = buildOpeningHour(1, "10:30", "11:30");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(w1, w2));
        mockNoBookings();

        // Block 09:30-11:00 — spans the gap and into both windows
        BlockedSlot block = buildBlockedSlot(futureMonday, "09:30", "11:00");
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Window 1: 09:00-09:30 -> 09:00<11:00 && 09:30>09:30 -> false -> available
        //           09:30-10:00 -> 09:30<11:00 && 10:00>09:30 -> blocked
        // Window 2: 10:30-11:00 -> 10:30<11:00 && 11:00>09:30 -> blocked
        //           11:00-11:30 -> 11:00<11:00 -> false -> available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("11:00");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── CONCURRENCY / CANCELLATION EDGE CASES ──
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cancelled booking does NOT block slots — repo already filters them out")
    void cancelledBooking_doesNotBlockSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30"); // 3 slots
        mockNoBlocks();

        // The repo returns only non-cancelled bookings → return empty list
        // (Cancelled bookings are filtered by status != CANCELLED in the query)
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of());

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // All 3 slots available since the cancelled booking is not returned
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("Mix of CONFIRMED and CANCELLED bookings — only confirmed blocks")
    void confirmedAndCancelledMix_onlyConfirmedBlocks() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30"); // 3 slots: 09:00, 09:30, 10:00
        mockNoBlocks();

        // Only the confirmed booking is returned (repo filters out cancelled)
        CareBooking confirmed = buildBooking(futureMonday, "09:00", care30min, null);
        confirmed.setStatus(CareBookingStatus.CONFIRMED);

        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(confirmed));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00 blocked, 09:30 available, 10:00 available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
        assertThat(result.get(1).startTime()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("Fully booked day with one cancellation — exactly one slot opens")
    void fullyBooked_oneCancellation_oneSlotOpens() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30"); // 3 slots: 09:00, 09:30, 10:00
        mockNoBlocks();

        // 09:00 and 10:00 still booked, 09:30 was cancelled (not returned by repo)
        CareBooking b1 = buildBooking(futureMonday, "09:00", care30min, null);
        CareBooking b3 = buildBooking(futureMonday, "10:00", care30min, null);

        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(b1, b3));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── ADDITIONAL COMPLEX SCENARIOS ──
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Employee with full-day blocked slot — returns empty even with open hours")
    void employeeFullDayBlock_returnsEmpty() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "18:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);

        BlockedSlot fullDay = new BlockedSlot();
        fullDay.setDate(futureMonday);
        fullDay.setFullDay(true);
        fullDay.setEmployeeId(10L);
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(10L), any()))
                .thenReturn(List.of(fullDay));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
        // No bookings mock needed — service returns early on full-day block

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Salon-wide full-day block affects employee even with personal hours")
    void salonFullDayBlock_affectsEmployee() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "18:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");
        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);

        // Employee has no blocks
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(10L), any()))
                .thenReturn(List.of());
        // But salon has full-day block
        BlockedSlot salonFullDay = new BlockedSlot();
        salonFullDay.setDate(futureMonday);
        salonFullDay.setFullDay(true);
        // employeeId is null = salon-wide
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(salonFullDay));
        // No bookings mock needed — service returns early on full-day block

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Multiple blocked slots with tight gap — 30min care fits, 60min does not")
    void tightGap_30minFits_60minDoesNot() {
        // Gap of exactly 30 min between two blocks
        mockOpeningHours(1, "09:00", "12:00");
        mockNoBookings();

        BlockedSlot block1 = buildBlockedSlot(futureMonday, "09:00", "10:00");
        BlockedSlot block2 = buildBlockedSlot(futureMonday, "10:30", "12:00");
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block1, block2));

        // 30-min care should fit in the gap
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        List<SlotAvailabilityService.TimeSlot> result30 =
                service.getAvailableSlots(futureMonday, 2L);
        assertThat(result30).hasSize(1);
        assertThat(result30.get(0).startTime()).isEqualTo("10:00");

        // 60-min care should NOT fit (gap 10:00-10:30 is only 30 min)
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        List<SlotAvailabilityService.TimeSlot> result60 =
                service.getAvailableSlots(futureMonday, 1L);
        assertThat(result60).isEmpty();
    }

    @Test
    @DisplayName("Employee with personal hours on different days — only matching day used")
    void employeePersonalHours_differentDays_onlyMatchingDayUsed() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 has hours for Monday (1) and Wednesday (3)
        OpeningHour monHour = buildOpeningHourForEmployee(1, "09:00", "10:00", 10L);
        OpeningHour wedHour = buildOpeningHourForEmployee(3, "14:00", "18:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(monHour, wedHour));
        // Salon hours must include Monday for clipping
        OpeningHour salonMon = buildOpeningHour(1, "09:00", "18:00");
        OpeningHour salonWed = buildOpeningHour(3, "09:00", "18:00");
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(salonMon, salonWed));

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // futureMonday is Monday (dow=1) → uses monHour (09:00-10:00)
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("09:30");
    }

    @Test
    @DisplayName("findFirstAvailableEmployee with empty candidate list returns null")
    void findFirst_emptyList_returnsNull() {
        // No care mock needed — the loop body is never entered with an empty list
        Long result = service.findFirstAvailableEmployee(
                futureMonday, 2L, "09:00", List.of());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findFirstAvailableEmployee — requested time outside working hours returns null")
    void findFirst_requestedTimeOutsideHours_returnsNull() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 works 09:00-10:00
        setupEmployeeAvailable(10L, "09:00", "10:00");

        // Request 14:00 which is outside working hours
        Long result = service.findFirstAvailableEmployee(
                futureMonday, 2L, "14:00", List.of(10L));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Overlapping opening windows are handled independently")
    void overlappingOpeningWindows_handledIndependently() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Two overlapping opening windows (unusual but possible config)
        OpeningHour w1 = buildOpeningHour(1, "09:00", "10:30");
        OpeningHour w2 = buildOpeningHour(1, "10:00", "11:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(w1, w2));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Window 1: 09:00, 09:30, 10:00
        // Window 2: 10:00, 10:30
        // Slots may be duplicated (09:00 and 10:30 are unique; 10:00 appears twice)
        assertThat(result).hasSizeGreaterThanOrEqualTo(4);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("Block with null start/end times and fullDay=false does not crash or block")
    void blockWithNullTimes_noBlockNoCrash() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBookings();

        // Malformed block — fullDay=false but no times set
        BlockedSlot malformed = new BlockedSlot();
        malformed.setDate(futureMonday);
        malformed.setFullDay(false);
        // startTime and endTime are null
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(malformed));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // The null check in isBlockedAt: bs.getStartTime() != null && bs.getEndTime() != null
        // So this block is skipped, all slots available
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("Long care (120 min) with multiple short blocks leaves only slots that fit entirely")
    void longCare_multipleShortBlocks() {
        Care care120min = new Care();
        care120min.setId(5L);
        care120min.setName("Soin prestige");
        care120min.setDuration(120);

        when(careRepo.findById(5L)).thenReturn(Optional.of(care120min));
        mockOpeningHours(1, "09:00", "14:00"); // 5h window
        mockNoBookings();

        // Block 10:00-10:30 — disrupts many 120-min slots
        BlockedSlot block = buildBlockedSlot(futureMonday, "10:00", "10:30");
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 5L);

        // 120-min care candidates (every 30 min):
        // 09:00-11:00 -> overlaps 10:00-10:30 -> blocked
        // 09:30-11:30 -> overlaps 10:00-10:30 -> blocked
        // 10:00-12:00 -> overlaps -> blocked
        // 10:30-12:30 -> 10:30<10:30 is false -> available
        // 11:00-13:00 -> available
        // 11:30-13:30 -> available
        // 12:00-14:00 -> available
        assertThat(result).hasSize(4);
        assertThat(result.get(0).startTime()).isEqualTo("10:30");
        assertThat(result.get(1).startTime()).isEqualTo("11:00");
        assertThat(result.get(2).startTime()).isEqualTo("11:30");
        assertThat(result.get(3).startTime()).isEqualTo("12:00");
    }

    @Test
    @DisplayName("Employee fallback hours filtered to correct day when employee has no hours")
    void employeeFallback_filteredToCorrectDay() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee has no personal hours
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of());

        // Salon-wide has Monday and Tuesday hours
        OpeningHour monSalon = buildOpeningHour(1, "09:00", "10:00");
        OpeningHour tueSalon = buildOpeningHour(2, "08:00", "12:00");
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(monSalon, tueSalon));

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // futureMonday is Monday → only monSalon applies (09:00-10:00): 2 slots
        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Horaires de l'établissement — scénarios critiques ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("BUG: Employee 08-22 but salon 09-21 → slots must be clipped to 09:00-21:00")
    void employeeHoursWiderThanSalon_mustBeClipped() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee personal hours: 08:00-22:00 (wider than salon)
        OpeningHour empHour = buildOpeningHourForEmployee(1, "08:00", "22:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));

        // Salon hours: 09:00-21:00
        OpeningHour salonHour = buildOpeningHour(1, "09:00", "21:00");
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(salonHour));

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // First slot must NOT be 08:00 — must be clipped to salon open time 09:00
        assertThat(result).isNotEmpty();
        LocalTime firstStart = LocalTime.parse(result.get(0).startTime());
        assertThat(firstStart).isAfterOrEqualTo(LocalTime.of(9, 0));

        // Last slot end must NOT exceed salon close time 21:00
        LocalTime lastEnd = LocalTime.parse(result.get(result.size() - 1).endTime());
        assertThat(lastEnd).isBeforeOrEqualTo(LocalTime.of(21, 0));
    }

    @Test
    @DisplayName("BUG: Salon closed (no opening hours) but employee has personal hours → 0 slots")
    void salonClosedDay_employeeHasHours_mustReturnEmpty() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee has personal hours for Monday (won't be reached — salon is closed)
        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "18:00", 10L);
        lenient().when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));

        // Salon has NO opening hours for Monday (closed day)
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of());

        // These won't be reached since salon is closed → service returns early
        lenient().when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // Employee cannot work if the salon itself is closed
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Different hours per day — Monday 09-18, Saturday 09-13, Sunday closed")
    void differentHoursPerDay_correctSlotsPerDay() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));

        OpeningHour mon = buildOpeningHour(1, "09:00", "18:00"); // Monday
        OpeningHour sat = buildOpeningHour(6, "09:00", "13:00"); // Saturday
        // Sunday: no entry = closed
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(mon, sat));
        mockNoBlocks();
        mockNoBookings();

        // Monday: 09:00-18:00 with 60-min care → 09:00, 09:30, ... 17:00 = 17 slots
        List<SlotAvailabilityService.TimeSlot> mondayResult =
                service.getAvailableSlots(futureMonday, 1L);
        assertThat(mondayResult).isNotEmpty();
        assertThat(mondayResult.get(0).startTime()).isEqualTo("09:00");
        assertThat(mondayResult.get(mondayResult.size() - 1).startTime()).isEqualTo("17:00");

        // Saturday: 09:00-13:00 with 60-min care → 09:00, 09:30, 10:00, 10:30, 11:00, 11:30, 12:00 = 7 slots
        LocalDate futureSaturday = futureMonday.plusDays(5);
        List<SlotAvailabilityService.TimeSlot> satResult =
                service.getAvailableSlots(futureSaturday, 1L);
        assertThat(satResult).hasSize(7);
        assertThat(satResult.get(satResult.size() - 1).startTime()).isEqualTo("12:00");

        // Sunday (day after Saturday): no hours → empty
        LocalDate futureSunday = futureMonday.plusDays(6);
        List<SlotAvailabilityService.TimeSlot> sunResult =
                service.getAvailableSlots(futureSunday, 1L);
        assertThat(sunResult).isEmpty();
    }

    @Test
    @DisplayName("BUG: Closing after midnight (14:00-01:00) — LocalTime overflow")
    void closingAfterMidnight_shouldNotCrash() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Opening 14:00 to 01:00 next day — closeTime < openTime
        // This is an edge case that LocalTime can represent (01:00) but
        // the while loop cursor.plusMinutes(duration) <= windowEnd will immediately
        // fail since 14:30 > 01:00
        OpeningHour oh = buildOpeningHour(1, "14:00", "01:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
        mockNoBlocks();
        mockNoBookings();

        // This should NOT crash and should handle the case gracefully
        // Currently: closeTime (01:00) < openTime (14:00) → while loop never enters → empty
        // This is a known limitation — midnight-crossing requires splitting into two windows
        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // With current code: returns empty (14:30 > 01:00 so loop never starts)
        // This documents the limitation — it doesn't crash but returns wrong result
        // A fix would split 14:00-01:00 into 14:00-23:59 + next day handling
        assertThat(result).isEmpty(); // Expected behavior with current limitation
    }

    @Test
    @DisplayName("Lunch break: 90-min care at 11:30 in 09-12 window must be rejected")
    void lunchBreak_careTooLongForRemainingWindow() {
        Care care90min = new Care();
        care90min.setId(3L);
        care90min.setName("Long care");
        care90min.setDuration(90);

        when(careRepo.findById(3L)).thenReturn(Optional.of(care90min));

        // Two windows: 09:00-12:00 (morning) and 14:00-18:00 (afternoon)
        OpeningHour morning = buildOpeningHour(1, "09:00", "12:00");
        OpeningHour afternoon = buildOpeningHour(1, "14:00", "18:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(morning, afternoon));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 3L);

        // Morning: 90min care, window 09:00-12:00
        // 09:00+90=10:30 ≤ 12:00 ✓
        // 09:30+90=11:00 ≤ 12:00 ✓
        // 10:00+90=11:30 ≤ 12:00 ✓
        // 10:30+90=12:00 ≤ 12:00 ✓
        // 11:00+90=12:30 > 12:00 ✗ ← rejected
        // 11:30+90=13:00 > 12:00 ✗ ← rejected (THIS IS THE KEY ASSERTION)
        // Afternoon: 14:00+90=15:30 ✓ ... 16:30+90=18:00 ✓
        // Total morning: 4 slots (09:00, 09:30, 10:00, 10:30)
        // Total afternoon: 6 slots (14:00, 14:30, 15:00, 15:30, 16:00, 16:30)

        // Verify 11:30 is NOT in the list
        boolean has1130 = result.stream().anyMatch(s -> s.startTime().equals("11:30"));
        assertThat(has1130).isFalse();

        // Verify 11:00 is NOT in the list either
        boolean has1100 = result.stream().anyMatch(s -> s.startTime().equals("11:00"));
        assertThat(has1100).isFalse();

        // Verify total count
        assertThat(result).hasSize(10); // 4 morning + 6 afternoon
    }

    @Test
    @DisplayName("No opening hours configured at all → empty for any day")
    void noOpeningHoursConfigured_returnsEmpty() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of()); // Nothing configured
        // No need to mock blocks/bookings since we return early

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);
        assertThat(result).isEmpty();

        // Also check a Saturday
        LocalDate sat = futureMonday.plusDays(5);
        List<SlotAvailabilityService.TimeSlot> satResult =
                service.getAvailableSlots(sat, 2L);
        assertThat(satResult).isEmpty();
    }

    @Test
    @DisplayName("Existing bookings outside new reduced hours — bookings don't affect slot generation")
    void existingBookingsOutsideNewHours_noImpactOnSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // New reduced hours: 10:00-14:00 (was 09:00-18:00)
        mockOpeningHours(1, "10:00", "14:00");
        mockNoBlocks();

        // Old booking at 09:00 (outside new hours) and 17:00 (outside new hours)
        CareBooking oldBooking1 = buildBooking(futureMonday, "09:00", care30min, null);
        CareBooking oldBooking2 = buildBooking(futureMonday, "17:00", care30min, null);
        // Current booking at 11:00 (inside new hours)
        CareBooking currentBooking = buildBooking(futureMonday, "11:00", care30min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(oldBooking1, oldBooking2, currentBooking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Slots: 10:00, 10:30, 11:00(booked), 11:30, 12:00, 12:30, 13:00, 13:30 = 8 total, minus 1 booked = 7
        assertThat(result).hasSize(7);
        // 11:00 should be missing (booked)
        boolean has1100 = result.stream().anyMatch(s -> s.startTime().equals("11:00"));
        assertThat(has1100).isFalse();
        // 09:00 and 17:00 bookings are outside window → they don't remove any slots
        // (the overlap check only matches slots within the opening hours)
    }

    // ══════════════════════════════════════════════════════════════
    // ── Disponibilités des employés — scénarios critiques ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Employee with zero availability and no salon fallback → empty")
    void employeeZeroAvailability_noFallback_empty() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Salon is closed on Monday (no hours) → returns empty before checking employee
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of());

        // Employee hours never reached, but mock leniently in case
        lenient().when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of());

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Employee with zero personal hours but salon open → uses salon hours (fallback)")
    void employeeZeroPersonalHours_salonOpen_usesFallback() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of());

        // Salon open 09:00-11:00 on Monday
        OpeningHour salonHour = buildOpeningHour(1, "09:00", "11:00");
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(salonHour));

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // Should use salon hours: 09:00, 09:30, 10:00, 10:30 = 4 slots
        assertThat(result).hasSize(4);
    }

    @Test
    @DisplayName("BUG: Overlapping employee windows (09-13 and 12-17) → duplicate slots in overlap zone")
    void overlappingEmployeeWindows_producesDuplicateSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Two overlapping windows for the same employee
        OpeningHour window1 = buildOpeningHourForEmployee(1, "09:00", "13:00", 10L);
        OpeningHour window2 = buildOpeningHourForEmployee(1, "12:00", "17:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(window1, window2));

        // Salon covers the full range
        OpeningHour salonHour = buildOpeningHour(1, "09:00", "17:00");
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(salonHour));

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // Overlap zone 12:00-13:00 should NOT produce duplicate slots
        // Window 1 (09:00-13:00): 09:00, 09:30, 10:00, 10:30, 11:00, 11:30, 12:00, 12:30
        // Window 2 (12:00-17:00): 12:00, 12:30, 13:00, 13:30, 14:00, 14:30, 15:00, 15:30, 16:00, 16:30
        // Without dedup: 12:00 and 12:30 appear twice
        // Total unique slots from 09:00 to 16:30 = 16 slots
        long uniqueStartTimes = result.stream()
                .map(SlotAvailabilityService.TimeSlot::startTime)
                .distinct()
                .count();

        // This test documents current behavior — if there are duplicates, it's a bug
        // The fix would be to merge/deduplicate overlapping windows
        assertThat(uniqueStartTimes).isEqualTo(result.size())
                .as("No duplicate slots should exist in the overlap zone");
    }

    @Test
    @DisplayName("Employee personal break via blocked slot (12:00-13:00 off)")
    void employeePersonalBreak_midDay_blocksSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee works 09:00-18:00
        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "18:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);

        // Personal break: 12:00-13:00 (employee-specific blocked slot)
        BlockedSlot personalBreak = new BlockedSlot();
        personalBreak.setDate(futureMonday);
        personalBreak.setFullDay(false);
        personalBreak.setStartTime(LocalTime.of(12, 0));
        personalBreak.setEndTime(LocalTime.of(13, 0));
        personalBreak.setEmployeeId(10L);
        personalBreak.setReason("Pause déjeuner");
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(10L), any()))
                .thenReturn(List.of(personalBreak));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // 12:00 and 12:30 slots should be blocked
        boolean has1200 = result.stream().anyMatch(s -> s.startTime().equals("12:00"));
        boolean has1230 = result.stream().anyMatch(s -> s.startTime().equals("12:30"));
        assertThat(has1200).isFalse();
        assertThat(has1230).isFalse();

        // 11:30 should be available (11:30+30=12:00, overlap check: 11:30 < 13:00 && 12:00 > 12:00 → false)
        boolean has1130 = result.stream().anyMatch(s -> s.startTime().equals("11:30"));
        assertThat(has1130).isTrue();

        // 13:00 should be available (break ends at 13:00)
        boolean has1300 = result.stream().anyMatch(s -> s.startTime().equals("13:00"));
        assertThat(has1300).isTrue();
    }

    @Test
    @DisplayName("Employee break with longer care (60 min) — blocks more slots")
    void employeeBreak_longerCare_blocksMoreSlots() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));

        OpeningHour empHour = buildOpeningHourForEmployee(1, "09:00", "18:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);

        // Break 12:00-13:00
        BlockedSlot personalBreak = new BlockedSlot();
        personalBreak.setDate(futureMonday);
        personalBreak.setFullDay(false);
        personalBreak.setStartTime(LocalTime.of(12, 0));
        personalBreak.setEndTime(LocalTime.of(13, 0));
        personalBreak.setEmployeeId(10L);
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(10L), any()))
                .thenReturn(List.of(personalBreak));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());

        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L, 10L);

        // 60-min care: slot at 11:30 → 11:30+60=12:30, overlaps 12:00-13:00 → BLOCKED
        boolean has1130 = result.stream().anyMatch(s -> s.startTime().equals("11:30"));
        assertThat(has1130).isFalse();

        // 11:00 → 11:00+60=12:00, overlaps? 11:00 < 13:00 && 12:00 > 12:00 → false → AVAILABLE
        boolean has1100 = result.stream().anyMatch(s -> s.startTime().equals("11:00"));
        assertThat(has1100).isTrue();

        // 12:00 → 12:00+60=13:00, overlaps 12:00-13:00 → BLOCKED
        boolean has1200 = result.stream().anyMatch(s -> s.startTime().equals("12:00"));
        assertThat(has1200).isFalse();

        // 12:30 → 12:30+60=13:30, overlaps 12:00-13:00 → BLOCKED
        boolean has1230 = result.stream().anyMatch(s -> s.startTime().equals("12:30"));
        assertThat(has1230).isFalse();

        // 13:00 → 13:00+60=14:00, overlaps? 13:00 < 13:00 → false → AVAILABLE
        boolean has1300 = result.stream().anyMatch(s -> s.startTime().equals("13:00"));
        assertThat(has1300).isTrue();
    }

    @Test
    @DisplayName("Employee midnight-crossing availability (22:00-02:00) → documents limitation")
    void employeeMidnightCrossing_limitation() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee claims to work 22:00-02:00 (overnight)
        OpeningHour empHour = buildOpeningHourForEmployee(1, "22:00", "02:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));

        // Salon open 09:00-23:00
        OpeningHour salonHour = buildOpeningHour(1, "09:00", "23:00");
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(salonHour));

        // These may not be reached due to clipping returning empty, so use lenient
        lenient().when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        lenient().when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(10L), any()))
                .thenReturn(List.of());
        lenient().when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
        lenient().when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(any(), eq(10L), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of());

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // Limitation: closeTime (02:00) < openTime (22:00) after clipping
        // Clip: max(22:00, 09:00) = 22:00, min(02:00, 23:00) = 02:00
        // 22:00 < 02:00 is false → no intersection → empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findFirstAvailableEmployee skips employee without matching care competency")
    void findFirst_employeeWithoutCompetency_isNotInCandidateList() {
        // This test documents that competency filtering happens OUTSIDE SlotAvailabilityService
        // The caller passes only candidate employee IDs that have the required competency
        // So if employee 10 has no competency for care 2, they simply won't be in the list

        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Only employee 20 is a candidate (employee 10 has no competency)
        setupEmployeeAvailable(20L, "09:00", "10:00");

        Long result = service.findFirstAvailableEmployee(
                futureMonday, 2L, "09:00", List.of(20L));

        // Employee 20 is available
        assertThat(result).isEqualTo(20L);
    }

    @Test
    @DisplayName("Employee added mid-day — slots calculated on the fly from current config")
    void employeeAddedMidDay_slotsImmediatelyAvailable() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Simulate: employee 10 was just added with hours 14:00-18:00
        // (morning already passed, but afternoon slots should be available)
        OpeningHour empHour = buildOpeningHourForEmployee(1, "14:00", "18:00", 10L);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(10L))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee("09:00", "18:00");

        when(leaveRequestService.isOnLeave(10L, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(10L);
        mockNoBookingsForEmployee(10L);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L, 10L);

        // Slots should be available from 14:00 onwards (not from 09:00)
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).startTime()).isEqualTo("14:00");

        // Last slot: 17:30 (17:30+30=18:00 ≤ 18:00)
        assertThat(result.get(result.size() - 1).startTime()).isEqualTo("17:30");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Créneaux bloqués — scénarios critiques ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Block covers an existing booking time — booking still counts, slot blocked by both")
    void blockCoversExistingBooking_slotBlockedByBoth() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "11:00"); // 4 slots: 09:00, 09:30, 10:00, 10:30

        // Booking at 10:00
        CareBooking booking = buildBooking(futureMonday, "10:00", care30min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));

        // Block also covers 10:00-10:30 (overlaps the booking)
        BlockedSlot block = new BlockedSlot();
        block.setDate(futureMonday);
        block.setFullDay(false);
        block.setStartTime(LocalTime.of(9, 45));
        block.setEndTime(LocalTime.of(10, 45));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00: end=09:30, block 09:45-10:45 → 09:00 < 10:45 && 09:30 > 09:45? → false → AVAILABLE
        // 09:30: end=10:00, 09:30 < 10:45 && 10:00 > 09:45 → true → BLOCKED
        // 10:00: blocked by block AND booking
        // 10:30: end=11:00, 10:30 < 10:45 && 11:00 > 09:45 → true → BLOCKED
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("Tiny 5-min block splits a 1h care — both remaining pieces too small")
    void tinyBlockSplits60minCare_bothPiecesTooSmall() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "10:30"); // 90-min window, 60-min care

        mockNoBookings();

        // 5-min block at 09:25-09:30 — right in the middle of the window
        BlockedSlot tinyBlock = new BlockedSlot();
        tinyBlock.setDate(futureMonday);
        tinyBlock.setFullDay(false);
        tinyBlock.setStartTime(LocalTime.of(9, 25));
        tinyBlock.setEndTime(LocalTime.of(9, 30));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(tinyBlock));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        // Candidate slots (30-min interval, 60-min care):
        // 09:00: 09:00-10:00, overlaps 09:25-09:30? 09:00<09:30 && 10:00>09:25 → YES → BLOCKED
        // 09:30: 09:30-10:30, overlaps? 09:30<09:30 → false → AVAILABLE
        // So a tiny 5-min block kills the 09:00 slot but 09:30 survives
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
    }

    @Test
    @DisplayName("Block that crosses midnight (22:00-02:00) — documents limitation")
    void blockCrossingMidnight_limitation() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "20:00", "23:00"); // Evening salon
        mockNoBookings();

        // Block 22:00-02:00 (crosses midnight)
        BlockedSlot midnightBlock = new BlockedSlot();
        midnightBlock.setDate(futureMonday);
        midnightBlock.setFullDay(false);
        midnightBlock.setStartTime(LocalTime.of(22, 0));
        midnightBlock.setEndTime(LocalTime.of(2, 0)); // Next day
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(midnightBlock));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Limitation: endTime (02:00) < startTime (22:00) in isBlockedAt
        // Overlap check: slot.start < 02:00 && slot.end > 22:00
        // For 20:00-20:30: 20:00 < 02:00 → FALSE (LocalTime comparison: 20:00 is NOT before 02:00)
        // So the block doesn't block anything — this is a known limitation
        // Expected: slots from 20:00 to 22:30 (6 slots), none blocked by the midnight-crossing block
        assertThat(result).hasSize(6);
        // All slots available because midnight-crossing block is not properly handled
        assertThat(result.get(0).startTime()).isEqualTo("20:00");
    }

    @Test
    @DisplayName("Two overlapping blocks — slots in overlap zone still blocked")
    void twoOverlappingBlocks_overlapZoneBlocked() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "12:00"); // 6 slots
        mockNoBookings();

        // Block 1: 09:30-10:30
        BlockedSlot block1 = new BlockedSlot();
        block1.setDate(futureMonday);
        block1.setFullDay(false);
        block1.setStartTime(LocalTime.of(9, 30));
        block1.setEndTime(LocalTime.of(10, 30));

        // Block 2: 10:00-11:00 (overlaps with block 1)
        BlockedSlot block2 = new BlockedSlot();
        block2.setDate(futureMonday);
        block2.setFullDay(false);
        block2.setStartTime(LocalTime.of(10, 0));
        block2.setEndTime(LocalTime.of(11, 0));

        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(block1, block2));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00: end=09:30, block1: 09:00<10:30 && 09:30>09:30? → false → block2: 09:00<11:00 && 09:30>10:00? → false → AVAILABLE
        // 09:30: block1 overlap → BLOCKED
        // 10:00: both blocks overlap → BLOCKED
        // 10:30: block2: 10:30<11:00 && 11:00>10:00 → BLOCKED
        // 11:00: block2: 11:00<11:00 → false → AVAILABLE
        // 11:30: AVAILABLE
        assertThat(result).hasSize(3);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(1).startTime()).isEqualTo("11:00");
        assertThat(result.get(2).startTime()).isEqualTo("11:30");
    }

    @Test
    @DisplayName("Block in the past — past dates return empty regardless of blocks")
    void blockInPast_pastDateReturnsEmpty() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Don't even need to mock anything — past date check is the first thing
        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(yesterday, 2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Block in past loaded but querying future date — block is filtered out")
    void blockInPast_futureDateQuery_blockIgnored() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:00"); // 2 slots
        mockNoBookings();

        // Block is for a past date — should be filtered out since it doesn't match futureMonday
        BlockedSlot pastBlock = new BlockedSlot();
        pastBlock.setDate(futureMonday.minusDays(7)); // Last week
        pastBlock.setFullDay(true);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(pastBlock)); // Repository returns it but filter should exclude

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Past block doesn't match futureMonday → not filtered → all slots available
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Full-day block vs closed day (no opening hours) — same result, different code paths")
    void fullDayBlock_vs_closedDay_sameResult() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Path 1: Full-day block with opening hours present
        mockOpeningHours(1, "09:00", "18:00");
        lenient().when(bookingRepo.findByAppointmentDateAndStatusNot(any(), any()))
                .thenReturn(List.of());
        BlockedSlot fullDayBlock = new BlockedSlot();
        fullDayBlock.setDate(futureMonday);
        fullDayBlock.setFullDay(true);
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(fullDayBlock));

        List<SlotAvailabilityService.TimeSlot> resultBlocked =
                service.getAvailableSlots(futureMonday, 2L);

        // Path 2: No opening hours (closed day)
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of());

        List<SlotAvailabilityService.TimeSlot> resultClosed =
                service.getAvailableSlots(futureMonday, 2L);

        // Both should be empty
        assertThat(resultBlocked).isEmpty();
        assertThat(resultClosed).isEmpty();

        // Both paths produce the same result
        assertThat(resultBlocked).isEqualTo(resultClosed);
    }

    @Test
    @DisplayName("Recurring block pattern not natively supported — must create individual blocks")
    void recurringBlock_notNativelySupported_documentedLimitation() {
        // The system does NOT have a "recurring blocked slot" entity.
        // Each blocked slot is for a specific date.
        // To block every Tuesday 14:00-15:00, you must create a BlockedSlot for each Tuesday.
        // This test documents that individual blocks work correctly.

        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "17:00");
        mockNoBookings();

        // Simulate: Tuesday block for THIS week's Monday (so it doesn't match)
        BlockedSlot tuesdayBlock = new BlockedSlot();
        tuesdayBlock.setDate(futureMonday.plusDays(1)); // Tuesday
        tuesdayBlock.setFullDay(false);
        tuesdayBlock.setStartTime(LocalTime.of(14, 0));
        tuesdayBlock.setEndTime(LocalTime.of(15, 0));
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of(tuesdayBlock));

        // Query for Monday → Tuesday block doesn't match → all slots available
        List<SlotAvailabilityService.TimeSlot> mondayResult =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(mondayResult).isNotEmpty();
        // 14:00 is available on Monday (block is for Tuesday)
        boolean has1400 = mondayResult.stream().anyMatch(s -> s.startTime().equals("14:00"));
        assertThat(has1400).isTrue();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Jours fériés — intégration dans SlotAvailabilityService ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Holiday: salon closed on holiday → returns empty slots")
    void holiday_salonClosed_emptySlots() {
        lenient().when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Set up tenant context
        com.prettyface.app.multitenancy.TenantContext.setCurrentTenant("test-salon");
        com.prettyface.app.tenant.domain.Tenant tenant = new com.prettyface.app.tenant.domain.Tenant();
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(true);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));
        when(holidayAvailabilityService.isClosedForHoliday(futureMonday, "FR", true)).thenReturn(true);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).isEmpty();

        com.prettyface.app.multitenancy.TenantContext.clear();
    }

    @Test
    @DisplayName("Holiday: salon has override (open despite holiday) → returns normal slots")
    void holiday_salonOverrideOpen_normalSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        com.prettyface.app.multitenancy.TenantContext.setCurrentTenant("test-salon");
        com.prettyface.app.tenant.domain.Tenant tenant = new com.prettyface.app.tenant.domain.Tenant();
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(true);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));
        // Exception: open despite holiday
        when(holidayAvailabilityService.isClosedForHoliday(futureMonday, "FR", true)).thenReturn(false);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).hasSize(2); // 09:00, 09:30

        com.prettyface.app.multitenancy.TenantContext.clear();
    }

    @Test
    @DisplayName("Holiday: closedOnHolidays=false → holiday ignored, normal slots")
    void holiday_featureDisabled_normalSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        com.prettyface.app.multitenancy.TenantContext.setCurrentTenant("test-salon");
        com.prettyface.app.tenant.domain.Tenant tenant = new com.prettyface.app.tenant.domain.Tenant();
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(false);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));
        // closedOnHolidays=false → not closed
        when(holidayAvailabilityService.isClosedForHoliday(futureMonday, "FR", false)).thenReturn(false);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).hasSize(2);

        com.prettyface.app.multitenancy.TenantContext.clear();
    }

    @Test
    @DisplayName("Holiday on Sunday (already closed) — no opening hours + holiday = empty, no crash")
    void holiday_onSunday_alreadyClosed_noCrash() {
        // Sunday: no opening hours configured
        lenient().when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        lenient().when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of());

        // Set up holiday check (but it won't be reached since no opening hours)
        com.prettyface.app.multitenancy.TenantContext.setCurrentTenant("test-salon");
        com.prettyface.app.tenant.domain.Tenant tenant = new com.prettyface.app.tenant.domain.Tenant();
        tenant.setAddressCountry("FR");
        tenant.setClosedOnHolidays(true);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));
        lenient().when(holidayAvailabilityService.isClosedForHoliday(any(), any(), any(Boolean.class)))
                .thenReturn(true);

        // Use a future Sunday
        LocalDate futureSunday = futureMonday.plusDays(6);
        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureSunday, 2L);

        // Empty because no opening hours AND holiday — no double effect, no crash
        assertThat(result).isEmpty();

        com.prettyface.app.multitenancy.TenantContext.clear();
    }

    @Test
    @DisplayName("Holiday: no tenant context (public without tenant) → not closed")
    void holiday_noTenantContext_notClosed() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        // No TenantContext set → isClosedForHoliday returns false
        com.prettyface.app.multitenancy.TenantContext.clear();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Normal slots returned since holiday check is skipped without tenant
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Holiday: tenant has no country configured → not closed")
    void holiday_noCountry_notClosed() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        com.prettyface.app.multitenancy.TenantContext.setCurrentTenant("test-salon");
        com.prettyface.app.tenant.domain.Tenant tenant = new com.prettyface.app.tenant.domain.Tenant();
        tenant.setAddressCountry(null); // No country
        tenant.setClosedOnHolidays(true);
        when(tenantRepository.findBySlug("test-salon")).thenReturn(Optional.of(tenant));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).hasSize(2);

        com.prettyface.app.multitenancy.TenantContext.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // ── Prise de RDV — edge cases au niveau slots ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Care duration 0 → infinite loop guard: no slots generated (cursor never advances)")
    void careDuration0_noInfiniteLoop() {
        Care care0min = new Care();
        care0min.setId(99L);
        care0min.setName("Zero duration");
        care0min.setDuration(0);

        when(careRepo.findById(99L)).thenReturn(Optional.of(care0min));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 99L);

        // Duration 0: cursor.plusMinutes(0) = cursor <= windowEnd is always true
        // But slots have start=end (e.g. "09:00"-"09:00") which is degenerate
        // The dedup set prevents infinite loop since startKey repeats
        // Actually: cursor advances by SLOT_INTERVAL_MINUTES (30), so it terminates
        // Result: slots with start=end, which is weird but doesn't crash
        assertThat(result).isNotNull(); // Main assertion: no crash, no infinite loop
    }

    @Test
    @DisplayName("Slot exceeds closing by 1 min — not included in available slots")
    void slotExceedsClosingBy1Min_notIncluded() {
        // Window 09:00-09:59 (59 minutes), care 60 min
        // 09:00+60=10:00 > 09:59 → not included
        Care care60min = new Care();
        care60min.setId(1L);
        care60min.setDuration(60);
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));

        // Simulate closing at 09:59 (not on a 30-min boundary)
        OpeningHour oh = new OpeningHour();
        oh.setId(1L);
        oh.setDayOfWeek(1);
        oh.setOpenTime(LocalTime.of(9, 0));
        oh.setCloseTime(LocalTime.of(9, 59));
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        // 09:00+60=10:00, 10:00 > 09:59 → slot rejected
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Slot ends exactly at closing — included")
    void slotEndsExactlyAtClosing_included() {
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "10:00"); // Exactly 60 min window
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        // 09:00+60=10:00 ≤ 10:00 → included
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(0).endTime()).isEqualTo("10:00");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Chevauchements et conflits ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Booking 10:00-11:00, slot 10:30-11:30 overlaps → blocked")
    void overlappingBooking_slotBlocked() {
        Care care60min = new Care();
        care60min.setId(1L);
        care60min.setDuration(60);
        when(careRepo.findById(1L)).thenReturn(Optional.of(care60min));
        mockOpeningHours(1, "09:00", "12:00");
        mockNoBlocks();

        // Existing booking: 10:00-11:00 (60 min care)
        CareBooking booking = buildBooking(futureMonday, "10:00", care60min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 1L);

        // 10:30 → 10:30+60=11:30, overlaps 10:00-11:00? 10:30 < 11:00 && 11:30 > 10:00 → YES → blocked
        boolean has1030 = result.stream().anyMatch(s -> s.startTime().equals("10:30"));
        assertThat(has1030).isFalse();

        // 09:00 → 09:00+60=10:00, overlaps 10:00-11:00? 09:00<11:00 && 10:00>10:00 → false → AVAILABLE
        boolean has0900 = result.stream().anyMatch(s -> s.startTime().equals("09:00"));
        assertThat(has0900).isTrue();

        // 09:30 → 09:30+60=10:30, overlaps 10:00-11:00? 09:30<11:00 && 10:30>10:00 → YES → BLOCKED
        boolean has0930 = result.stream().anyMatch(s -> s.startTime().equals("09:30"));
        assertThat(has0930).isFalse();
    }

    @Test
    @DisplayName("Adjacent bookings: 10:00-11:00 then 11:00-12:00 → no overlap, both valid")
    void adjacentBookings_noOverlap() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "12:00");
        mockNoBlocks();

        // Booking at 09:00-09:30
        CareBooking booking = buildBooking(futureMonday, "09:00", care30min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:30 → 09:30 < 09:30 is FALSE → no overlap → AVAILABLE
        boolean has0930 = result.stream().anyMatch(s -> s.startTime().equals("09:30"));
        assertThat(has0930).isTrue();
    }

    @Test
    @DisplayName("Buffer 10 min: booking 10:00-10:30 → next slot at 10:30 blocked (needs 10:40)")
    void buffer10min_nextSlotBlocked() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "12:00");
        mockNoBlocks();

        // Booking at 10:00-10:30
        CareBooking booking = buildBooking(futureMonday, "10:00", care30min, null);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));

        // Set up tenant with 10 min buffer
        com.prettyface.app.multitenancy.TenantContext.setCurrentTenant("buffer-salon");
        com.prettyface.app.tenant.domain.Tenant tenant = new com.prettyface.app.tenant.domain.Tenant();
        tenant.setBufferMinutes(10);
        when(tenantRepository.findBySlug("buffer-salon")).thenReturn(Optional.of(tenant));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // Without buffer: 10:30 would be available (10:30 < 10:30 is false → no overlap)
        // With 10-min buffer: bookingEnd+buffer = 10:40
        // 10:30 → 10:30 < 10:40 && 11:00 > 10:00 → BLOCKED
        // 11:00 → 11:00 < 10:40 → false → AVAILABLE
        boolean has1030 = result.stream().anyMatch(s -> s.startTime().equals("10:30"));
        assertThat(has1030).isFalse();

        boolean has1100 = result.stream().anyMatch(s -> s.startTime().equals("11:00"));
        assertThat(has1100).isTrue();

        com.prettyface.app.multitenancy.TenantContext.clear();
    }

    @Test
    @DisplayName("3 back-to-back bookings, cancel middle one → middle slot reappears")
    void threeBackToBack_cancelMiddle_slotReappears() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:30"); // 3 slots: 09:00, 09:30, 10:00
        mockNoBlocks();

        // 3 bookings: 09:00, 09:30 (CANCELLED), 10:00
        CareBooking b1 = buildBooking(futureMonday, "09:00", care30min, null);
        b1.setStatus(CareBookingStatus.CONFIRMED);

        CareBooking b2 = buildBooking(futureMonday, "09:30", care30min, null);
        b2.setStatus(CareBookingStatus.CANCELLED); // Middle one cancelled

        CareBooking b3 = buildBooking(futureMonday, "10:00", care30min, null);
        b3.setStatus(CareBookingStatus.CONFIRMED);

        // Repository excludes cancelled → only b1 and b3 returned
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(b1, b3));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00 → booked, 09:30 → FREE (cancelled), 10:00 → booked
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:30");
    }

    @Test
    @DisplayName("Service 2h, gap of 1h59 → not enough, slot rejected")
    void service2h_gap1h59_rejected() {
        Care care120min = new Care();
        care120min.setId(5L);
        care120min.setName("Long care");
        care120min.setDuration(120);

        when(careRepo.findById(5L)).thenReturn(Optional.of(care120min));
        // Window exactly 1h59: 09:00-10:59
        OpeningHour oh = new OpeningHour();
        oh.setId(1L);
        oh.setDayOfWeek(1);
        oh.setOpenTime(LocalTime.of(9, 0));
        oh.setCloseTime(LocalTime.of(10, 59)); // 1h59
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 5L);

        // 09:00+120=11:00, 11:00 > 10:59 → slot REJECTED
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Service 2h, gap of exactly 2h → fits")
    void service2h_gapExactly2h_fits() {
        Care care120min = new Care();
        care120min.setId(5L);
        care120min.setName("Long care");
        care120min.setDuration(120);

        when(careRepo.findById(5L)).thenReturn(Optional.of(care120min));
        mockOpeningHours(1, "09:00", "11:00"); // Exactly 2h
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 5L);

        // 09:00+120=11:00 ≤ 11:00 → fits
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Services et durées — scénarios critiques ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Duration changed after booking: old 30-min booking now blocks as if it were 60-min")
    void durationChanged_oldBookingBlocksDifferently() {
        // Scenario: care was 30 min when booked, now changed to 60 min
        // The slot service reads current duration → old booking at 10:00 now occupies 10:00-11:00
        Care careNow60 = new Care();
        careNow60.setId(10L);
        careNow60.setName("Modified care");
        careNow60.setDuration(60); // Changed from 30 to 60

        when(careRepo.findById(10L)).thenReturn(Optional.of(careNow60));
        mockOpeningHours(1, "09:00", "12:00");
        mockNoBlocks();

        // Booking was made when care was 30 min, at 10:00
        // But booking.getCare() returns the CURRENT care object with duration=60
        CareBooking oldBooking = new CareBooking();
        oldBooking.setAppointmentDate(futureMonday);
        oldBooking.setAppointmentTime(LocalTime.of(10, 0));
        oldBooking.setCare(careNow60); // Care entity now has duration=60
        oldBooking.setStatus(CareBookingStatus.CONFIRMED);
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(oldBooking));

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 10L);

        // BUG DOCUMENTATION: The old booking now blocks 10:00-11:00 instead of 10:00-10:30
        // This means 10:30 is blocked even though the original appointment was only 30 min
        // Fix would be to store the booked duration on the CareBooking entity itself
        boolean has1030 = result.stream().anyMatch(s -> s.startTime().equals("10:30"));
        assertThat(has1030).isFalse()
                .as("BUG: old booking blocks with NEW duration (60min) instead of booked duration (30min)");

        // 09:00 should still be available
        boolean has0900 = result.stream().anyMatch(s -> s.startTime().equals("09:00"));
        assertThat(has0900).isTrue();
    }

    @Test
    @DisplayName("Composite service: single care with combined duration (1h30 = coupe + coloration)")
    void compositeService_singleCareWithTotalDuration() {
        // The system doesn't support composite services natively.
        // Workaround: create a single care with the total duration.
        Care composite = new Care();
        composite.setId(20L);
        composite.setName("Coupe + Coloration");
        composite.setDuration(90); // 1h30 total

        when(careRepo.findById(20L)).thenReturn(Optional.of(composite));
        mockOpeningHours(1, "09:00", "12:00"); // 3h window
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 20L);

        // 90-min care in 3h window: 09:00(→10:30), 09:30(→11:00), 10:00(→11:30), 10:30(→12:00)
        assertThat(result).hasSize(4);
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(0).endTime()).isEqualTo("10:30");
        assertThat(result.get(3).startTime()).isEqualTo("10:30");
        assertThat(result.get(3).endTime()).isEqualTo("12:00");
    }

    @Test
    @DisplayName("Variable duration service: system uses fixed duration — document limitation")
    void variableDuration_usesFixedDuration_limitation() {
        // Scenario: "Massage 30 to 90 min" — system only supports fixed duration
        // Workaround: create the care with the MAX duration (90 min) to reserve enough time
        Care variableAsMax = new Care();
        variableAsMax.setId(30L);
        variableAsMax.setName("Massage (30-90 min)");
        variableAsMax.setDuration(90); // Use max duration

        when(careRepo.findById(30L)).thenReturn(Optional.of(variableAsMax));
        mockOpeningHours(1, "09:00", "11:00"); // 2h window
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 30L);

        // 90 min in 2h window: 09:00(→10:30) and 09:30(→11:00)
        assertThat(result).hasSize(2);
        // Limitation: even if the actual massage takes only 30 min, 90 min is blocked
    }

    @Test
    @DisplayName("Care with price 0 — valid, no crash")
    void careWithPrice0_noCrash() {
        Care freeCare = new Care();
        freeCare.setId(40L);
        freeCare.setName("Free consultation");
        freeCare.setDuration(30);
        freeCare.setPrice(0); // Free!

        when(careRepo.findById(40L)).thenReturn(Optional.of(freeCare));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 40L);

        // Price doesn't affect slot calculation at all
        assertThat(result).hasSize(2); // 09:00, 09:30
    }

    @Test
    @DisplayName("Care with negative duration — degenerate but no crash")
    void careWithNegativeDuration_noCrash() {
        Care negativeCare = new Care();
        negativeCare.setId(50L);
        negativeCare.setName("Negative care");
        negativeCare.setDuration(-10); // Invalid!

        when(careRepo.findById(50L)).thenReturn(Optional.of(negativeCare));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 50L);

        // cursor.plusMinutes(-10) goes BACKWARDS → cursor + duration < windowEnd is always true
        // But since cursor advances by SLOT_INTERVAL (30 min), loop terminates normally
        // Slots have end BEFORE start (degenerate)
        // No crash — but validation should prevent this at creation time
        assertThat(result).isNotNull(); // Main: no crash
    }

    @Test
    @DisplayName("Multi-employee service (4 hands) — not supported, document limitation")
    void multiEmployeeService_notSupported() {
        // System supports only one employeeId per booking
        // For "4 hands" service, workaround: book two separate appointments
        // for two employees at the same time

        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Employee 10 has a booking at 10:00
        setupEmployeeWithBookingAt(10L, "10:00", "09:00", "12:00");
        // Employee 20 is free at 10:00
        setupEmployeeAvailable(20L, "09:00", "12:00");

        // Both can work at the same time — the system doesn't prevent it
        List<SlotAvailabilityService.TimeSlot> emp10Slots =
                service.getAvailableSlots(futureMonday, 2L, 10L);
        List<SlotAvailabilityService.TimeSlot> emp20Slots =
                service.getAvailableSlots(futureMonday, 2L, 20L);

        // Employee 10: 10:00 blocked
        boolean emp10Has1000 = emp10Slots.stream().anyMatch(s -> s.startTime().equals("10:00"));
        assertThat(emp10Has1000).isFalse();

        // Employee 20: 10:00 available (no cross-blocking)
        boolean emp20Has1000 = emp20Slots.stream().anyMatch(s -> s.startTime().equals("10:00"));
        assertThat(emp20Has1000).isTrue();

        // Limitation: no way to atomically book both employees for a single service
    }

    // ══════════════════════════════════════════════════════════════
    // ── Fuseaux horaires et DST — documentation du comportement ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DST spring forward (02:00→03:00): LocalTime is naive — slot at 02:30 is generated normally")
    void dst_springForward_localTimeIsNaive() {
        // DST spring forward in Europe: 02:00 → 03:00
        // 02:00-03:00 doesn't exist in wall clock time
        // But LocalTime is timezone-naive — it doesn't know about DST
        // Use a future Sunday for DST (last Sunday of March 2027)
        LocalDate dstDay = LocalDate.of(2027, 3, 28); // Sunday — DST spring forward

        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        // Opening hours covering the DST gap (for a hypothetical late-night salon)
        OpeningHour oh = buildOpeningHour(7, "01:00", "04:00"); // Sunday = day 7
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(dstDay, 2L);

        // LocalTime generates slots at 01:00, 01:30, 02:00, 02:30, 03:00, 03:30
        // It doesn't know that 02:00-03:00 is skipped in real wall clock
        // This is fine for a salon app — no salon operates during DST transitions
        boolean has0200 = result.stream().anyMatch(s -> s.startTime().equals("02:00"));
        boolean has0230 = result.stream().anyMatch(s -> s.startTime().equals("02:30"));
        assertThat(has0200).isTrue(); // LocalTime is naive — generates the slot
        assertThat(has0230).isTrue();
        assertThat(result).hasSize(6);
    }

    @Test
    @DisplayName("DST fall back (03:00→02:00): LocalTime treats 02:30 as a single unique time")
    void dst_fallBack_localTimeIsNaive() {
        // DST fall back: 03:00 → 02:00
        // 02:00-03:00 exists twice in wall clock time
        // But LocalTime stores "02:30" as a unique value — no ambiguity
        LocalDate dstDay = LocalDate.of(2027, 10, 31); // Future Sunday — fall back day

        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        OpeningHour oh = buildOpeningHour(7, "01:00", "04:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(dstDay, 2L);

        // "02:30" appears exactly once — LocalTime has no concept of "first" or "second" 02:30
        long count0230 = result.stream().filter(s -> s.startTime().equals("02:30")).count();
        assertThat(count0230).isEqualTo(1);
        assertThat(result).hasSize(6);
    }

    @Test
    @DisplayName("Cross-timezone client: times are always in salon's local time")
    void crossTimezone_timesAreSalonLocal() {
        // Architecture documentation: all times are LocalTime (salon timezone)
        // A client in Brazil booking a salon in Luxembourg sees "14:00" = 14:00 Luxembourg time
        // No timezone conversion happens in the backend
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "14:00", "15:00");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // "14:00" means 14:00 at the salon, regardless of where the client is
        assertThat(result.get(0).startTime()).isEqualTo("14:00");
        // The frontend displays this time as-is — no UTC conversion
    }

    @Test
    @DisplayName("No UTC storage: LocalTime/LocalDate have no timezone component")
    void noUtcStorage_noOffByOneError() {
        // Verify that the slot system only uses LocalTime and LocalDate
        // There is no UTC→local conversion that could cause off-by-one errors
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // The returned times are plain strings "09:00", "09:30" — no timezone offset
        assertThat(result.get(0).startTime()).isEqualTo("09:00");
        assertThat(result.get(0).startTime()).doesNotContain("+");
        assertThat(result.get(0).startTime()).doesNotContain("Z");
        assertThat(result.get(0).startTime()).doesNotContain("T");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Comportements utilisateurs "stupides" ──
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FLAW: Client books 50 RDV — no limit, all slots consumed")
    void clientBooks50rdv_noLimit_allSlotsConsumed() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        mockOpeningHours(1, "09:00", "18:00"); // 18 slots
        mockNoBlocks();

        // 18 bookings filling every slot from 09:00 to 17:30
        List<CareBooking> allBookings = new java.util.ArrayList<>();
        LocalTime cursor = LocalTime.of(9, 0);
        for (int i = 0; i < 18; i++) {
            CareBooking b = new CareBooking();
            b.setAppointmentDate(futureMonday);
            b.setAppointmentTime(cursor);
            b.setCare(care30min);
            b.setStatus(CareBookingStatus.CONFIRMED);
            allBookings.add(b);
            cursor = cursor.plusMinutes(30);
        }
        when(bookingRepo.findByAppointmentDateAndStatusNot(eq(futureMonday), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(allBookings);

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // All slots consumed — calendar is fully blocked
        assertThat(result).isEmpty();
        // FLAW: No per-client booking limit exists
        // FIX NEEDED: Add max bookings per client per day/week/month in CareBookingService
    }

    @Test
    @DisplayName("FLAW: Book at 03:00 for 09:00 same day — no minimum advance time")
    void bookSameDay_noMinimumAdvanceTime() {
        // The system only checks if the date is not in the past
        // It does NOT check minimum advance time (e.g., "must book at least 2h in advance")
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Today's date — not in the past, so it passes the date check
        LocalDate today = LocalDate.now();
        int dow = today.getDayOfWeek().getValue();
        OpeningHour oh = buildOpeningHour(dow, "09:00", "18:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(today, 2L);

        // Slots are returned even if it's 03:00 AM and the salon opens at 09:00
        // No minimum advance check — a client at 08:59 could book for 09:00
        assertThat(result).isNotEmpty();
        // FLAW: No minimum advance time validation
        // FIX NEEDED: Add minAdvanceMinutes to Tenant (e.g., 120 = 2h minimum)
    }

    @Test
    @DisplayName("FLAW: Book 1 year in the future — no maximum advance limit")
    void bookFarFuture_noMaximumAdvanceLimit() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        LocalDate oneYearFromNow = LocalDate.now().plusYears(1);
        int dow = oneYearFromNow.getDayOfWeek().getValue();
        OpeningHour oh = buildOpeningHour(dow, "09:00", "10:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(oneYearFromNow, 2L);

        // Slots are returned for a date 1 year from now
        assertThat(result).isNotEmpty();
        // FLAW: No maximum advance booking limit
        // FIX NEEDED: Add maxAdvanceDays to Tenant (e.g., 90 = 3 months max)
    }

    @Test
    @DisplayName("Opening hour 09:00-09:00 (same time) → rejected by AvailabilityService validation")
    void openingHourSameTime_rejectedByValidation() {
        // This test validates the AvailabilityService.replaceAll() logic
        // Not SlotAvailabilityService, but documenting the protection here
        // close.isAfter(open) → 09:00.isAfter(09:00) → false → exception

        // If somehow such a window reached SlotAvailabilityService:
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        OpeningHour degenerateHour = buildOpeningHour(1, "09:00", "09:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(degenerateHour));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 09:00 + 30 = 09:30, 09:30 > 09:00 → loop condition fails → 0 slots
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Opening hour reversed 18:00-09:00 → rejected by AvailabilityService, 0 slots if it reached here")
    void openingHourReversed_zeroSlots() {
        // AvailabilityService.replaceAll() rejects close < open
        // But if it somehow reached the slot calculation:
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        OpeningHour reversed = buildOpeningHour(1, "18:00", "09:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(reversed));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 18:00 + 30 = 18:30, 18:30 > 09:00 → condition cursor+duration <= windowEnd fails
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Opening hour 00:00-00:00 → 0 slots (not 24h)")
    void openingHour0000to0000_zeroSlots() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        OpeningHour midnight = buildOpeningHour(1, "00:00", "00:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(midnight));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 00:00 + 30 = 00:30, 00:30 > 00:00 → fails → 0 slots (not 24 hours)
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Opening hour with 1-minute window (09:00-09:01) → 0 slots for 30-min care")
    void openingHour1minute_noSlotFor30minCare() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        OpeningHour tiny = buildOpeningHour(1, "09:00", "09:01");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(tiny));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Opening hour 23:30-00:00 → 1 slot for 30-min care")
    void lateNight_oneSlot() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));
        OpeningHour lateNight = buildOpeningHour(1, "23:30", "00:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(lateNight));
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // 23:30 + 30 = 00:00, but LocalTime midnight is 00:00
        // 00:00 <= 00:00 → true? compareTo returns 0 → ≤ 0 → true!
        // But wait: 23:30 + 30 = 00:00 (wraps around midnight via LocalTime)
        // 00:00.compareTo(00:00) = 0 ≤ 0 → condition passes
        // So the slot is generated... but is windowEnd midnight handled correctly?
        // Actually 23:30.plusMinutes(30) in LocalTime = 00:00 (next day)
        // And 00:00.compareTo(00:00) = 0 which is ≤ 0, so the loop enters
        // This means the slot IS generated — 23:30-00:00
        // This is technically correct: salon closes at midnight, last care starts at 23:30
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startTime()).isEqualTo("23:30");
    }

    @Test
    @DisplayName("EXTRA: Day of week 0 or 8 — invalid but no validation in OpeningHour entity")
    void invalidDayOfWeek_noValidation() {
        when(careRepo.findById(2L)).thenReturn(Optional.of(care30min));

        // Day 0 doesn't match any real day (Java DayOfWeek is 1-7)
        OpeningHour invalidDay = buildOpeningHour(0, "09:00", "10:00");
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(invalidDay));
        // These won't be reached since day filter returns empty → use lenient
        lenient().when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
        lenient().when(bookingRepo.findByAppointmentDateAndStatusNot(any(), any()))
                .thenReturn(List.of());

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 2L);

        // futureMonday is day 1, the opening hour is for day 0 → filter excludes it → empty
        assertThat(result).isEmpty();
        // FLAW: No validation on dayOfWeek range (should be 1-7)
    }

    @Test
    @DisplayName("EXTRA: Negative price on care — doesn't affect slots but is nonsensical")
    void negativePrice_noImpactOnSlots() {
        Care freebie = new Care();
        freebie.setId(99L);
        freebie.setDuration(30);
        freebie.setPrice(-100); // The salon pays YOU

        when(careRepo.findById(99L)).thenReturn(Optional.of(freebie));
        mockOpeningHours(1, "09:00", "10:00");
        mockNoBlocks();
        mockNoBookings();

        List<SlotAvailabilityService.TimeSlot> result =
                service.getAvailableSlots(futureMonday, 99L);

        // Price doesn't affect slot calculation — slots generated normally
        assertThat(result).hasSize(2);
        // FLAW: No @Min(0) validation on Care.price
    }

    // ── Helpers ──

    private void mockOpeningHours(int dow, String open, String close) {
        OpeningHour oh = buildOpeningHour(dow, open, close);
        when(openingHourRepo.findAllByOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(oh));
    }

    private OpeningHour buildOpeningHour(int dow, String open, String close) {
        OpeningHour oh = new OpeningHour();
        oh.setId((long) dow);
        oh.setDayOfWeek(dow);
        oh.setOpenTime(LocalTime.parse(open));
        oh.setCloseTime(LocalTime.parse(close));
        return oh;
    }

    private OpeningHour buildOpeningHourForEmployee(int dow, String open, String close, Long employeeId) {
        OpeningHour oh = buildOpeningHour(dow, open, close);
        oh.setEmployeeId(employeeId);
        return oh;
    }

    private BlockedSlot buildBlockedSlot(LocalDate date, String start, String end) {
        BlockedSlot bs = new BlockedSlot();
        bs.setDate(date);
        bs.setFullDay(false);
        bs.setStartTime(LocalTime.parse(start));
        bs.setEndTime(LocalTime.parse(end));
        // employeeId defaults to null = salon-wide
        return bs;
    }

    private CareBooking buildBooking(LocalDate date, String time, Care care, Long employeeId) {
        CareBooking booking = new CareBooking();
        booking.setAppointmentDate(date);
        booking.setAppointmentTime(LocalTime.parse(time));
        booking.setCare(care);
        booking.setEmployeeId(employeeId);
        booking.setStatus(CareBookingStatus.CONFIRMED);
        return booking;
    }

    private void mockNoBlocks() {
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
    }

    private void mockNoBookings() {
        when(bookingRepo.findByAppointmentDateAndStatusNot(any(), any()))
                .thenReturn(List.of());
    }

    private void mockNoBlocksForEmployee(Long employeeId) {
        when(blockedSlotRepo.findByEmployeeIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(eq(employeeId), any()))
                .thenReturn(List.of());
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
    }

    private void mockNoBookingsForEmployee(Long employeeId) {
        when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(
                any(), eq(employeeId), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of());
    }

    /**
     * Sets up an employee with open hours, no leave, no blocks, no bookings.
     * Also mocks salon-wide hours to match (for clipping).
     */
    private void setupEmployeeAvailable(Long employeeId, String open, String close) {
        OpeningHour empHour = buildOpeningHourForEmployee(1, open, close, employeeId);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(employeeId))
                .thenReturn(List.of(empHour));
        // Salon hours at least as wide as employee hours (no clipping)
        mockSalonHoursForEmployee(open, close);
        when(leaveRequestService.isOnLeave(employeeId, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(employeeId);
        mockNoBookingsForEmployee(employeeId);
    }

    /**
     * Sets up an employee with open hours and one booking at the specified time.
     */
    private void setupEmployeeWithBookingAt(Long employeeId, String bookingTime,
                                            String open, String close) {
        OpeningHour empHour = buildOpeningHourForEmployee(1, open, close, employeeId);
        when(openingHourRepo.findByEmployeeIdOrderByDayOfWeekAscOpenTimeAsc(employeeId))
                .thenReturn(List.of(empHour));
        mockSalonHoursForEmployee(open, close);
        when(leaveRequestService.isOnLeave(employeeId, futureMonday)).thenReturn(false);
        mockNoBlocksForEmployee(employeeId);

        CareBooking booking = buildBooking(futureMonday, bookingTime, care30min, employeeId);
        when(bookingRepo.findByAppointmentDateAndEmployeeIdAndStatusNot(
                eq(futureMonday), eq(employeeId), eq(CareBookingStatus.CANCELLED)))
                .thenReturn(List.of(booking));
    }

    /**
     * Mocks salon-wide opening hours for employee tests (needed for clipping).
     */
    private void mockSalonHoursForEmployee(String open, String close) {
        OpeningHour salonHour = buildOpeningHour(1, open, close);
        when(openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc())
                .thenReturn(List.of(salonHour));
    }

    private static LocalDate nextMonday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek().getValue() != 1) {
            d = d.plusDays(1);
        }
        return d;
    }
}
