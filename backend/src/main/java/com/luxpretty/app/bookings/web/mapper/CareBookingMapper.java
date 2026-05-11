package com.luxpretty.app.bookings.web.mapper;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.web.dto.CareBookingDetailedResponse;
import com.luxpretty.app.bookings.web.dto.CareBookingRequest;
import com.luxpretty.app.bookings.web.dto.CareBookingResponse;
import com.luxpretty.app.bookings.web.dto.CareInfo;
import com.luxpretty.app.bookings.web.dto.UserInfo;

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
        return toDetailedResponse(b, null, null);
    }

    public static CareBookingDetailedResponse toDetailedResponse(CareBooking b, String employeeName) {
        return toDetailedResponse(b, employeeName, null);
    }

    public static CareBookingDetailedResponse toDetailedResponse(CareBooking b, String employeeName, String salonClientName) {
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
                employeeName,
                b.getSalonClientId(),
                salonClientName
        );
    }

    public static void updateEntity(CareBooking b, CareBookingRequest req) {
        b.setQuantity(req.quantity());
        b.setAppointmentDate(req.appointmentDate());
        b.setAppointmentTime(req.appointmentTime());
        b.setStatus(req.status());
        b.setEmployeeId(req.employeeId());
    }
}

