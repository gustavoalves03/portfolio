package com.fleurdecoquillage.app.bookings.web.mapper;

import com.fleurdecoquillage.app.bookings.domain.CareBooking;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingRequest;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingResponse;

public class CareBookingMapper {
    public static CareBookingResponse toResponse(CareBooking b) {
        return new CareBookingResponse(
                b.getId(),
                b.getUser().getId(),
                b.getCare().getId(),
                b.getQuantity(),
                b.getStatus(),
                b.getCreatedAt()
        );
    }

    public static void updateEntity(CareBooking b, CareBookingRequest req) {
        b.setQuantity(req.quantity());
        b.setStatus(req.status());
    }
}

