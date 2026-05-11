package com.luxpretty.app.bookings.web.mapper;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.web.dto.CareBookingRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class CareBookingMapperTests {

    @Test
    void updateEntity_writesEmployeeId_whenProvided() {
        CareBooking b = new CareBooking();
        CareBookingRequest req = new CareBookingRequest(
                1L, 2L, 1,
                LocalDate.of(2026, 6, 1),
                LocalTime.of(10, 0),
                CareBookingStatus.PENDING,
                null,
                17L
        );

        CareBookingMapper.updateEntity(b, req);

        assertThat(b.getEmployeeId()).isEqualTo(17L);
    }

    @Test
    void updateEntity_leavesEmployeeIdNull_whenRequestHasNone() {
        CareBooking b = new CareBooking();
        CareBookingRequest req = new CareBookingRequest(
                1L, 2L, 1,
                LocalDate.of(2026, 6, 1),
                LocalTime.of(10, 0),
                CareBookingStatus.PENDING,
                null,
                null
        );

        CareBookingMapper.updateEntity(b, req);

        assertThat(b.getEmployeeId()).isNull();
    }
}
