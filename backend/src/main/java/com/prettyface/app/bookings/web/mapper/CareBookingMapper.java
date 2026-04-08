package com.prettyface.app.bookings.web.mapper;

import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.web.dto.CareBookingDetailedResponse;
import com.prettyface.app.bookings.web.dto.CareBookingRequest;
import com.prettyface.app.bookings.web.dto.CareBookingResponse;
import com.prettyface.app.bookings.web.dto.CareInfo;
import com.prettyface.app.bookings.web.dto.UserInfo;

public class CareBookingMapper {
    public static CareBookingResponse toResponse(CareBooking b) {
        return new CareBookingResponse(
                b.getId(),
                b.getUser().getId(),
                b.getCare().getId(),
                b.getQuantity(),
                b.getAppointmentDate(),
                b.getAppointmentTime(),
                b.getStatus(),
                b.getCreatedAt()
        );
    }

    public static CareBookingDetailedResponse toDetailedResponse(CareBooking b) {
        return toDetailedResponse(b, null);
    }

    public static CareBookingDetailedResponse toDetailedResponse(CareBooking b, String employeeName) {
        UserInfo userInfo = new UserInfo(
                b.getUser().getId(),
                b.getUser().getName(),
                b.getUser().getEmail()
        );

        CareInfo careInfo = new CareInfo(
                b.getCare().getId(),
                b.getCare().getName(),
                b.getCare().getPrice(),
                b.getCare().getDuration()
        );

        return new CareBookingDetailedResponse(
                b.getId(),
                userInfo,
                careInfo,
                b.getQuantity(),
                b.getAppointmentDate(),
                b.getAppointmentTime(),
                b.getStatus(),
                b.getCreatedAt(),
                b.getEmployeeId(),
                employeeName
        );
    }

    public static void updateEntity(CareBooking b, CareBookingRequest req) {
        b.setQuantity(req.quantity());
        b.setAppointmentDate(req.appointmentDate());
        b.setAppointmentTime(req.appointmentTime());
        b.setStatus(req.status());
    }
}

