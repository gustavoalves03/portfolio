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

    private void mockNoBlocks() {
        when(blockedSlotRepo.findByDateGreaterThanEqualOrderByDateAscStartTimeAsc(any()))
                .thenReturn(List.of());
    }

    private void mockNoBookings() {
        when(bookingRepo.findByAppointmentDateAndStatusNot(any(), any()))
                .thenReturn(List.of());
    }

    private static LocalDate nextMonday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek().getValue() != 1) {
            d = d.plusDays(1);
        }
        return d;
    }
}
