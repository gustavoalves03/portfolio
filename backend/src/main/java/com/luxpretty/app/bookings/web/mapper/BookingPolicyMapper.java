package com.luxpretty.app.bookings.web.mapper;

import com.luxpretty.app.bookings.domain.BookingPolicy;
import com.luxpretty.app.bookings.web.dto.BookingPolicyResponse;

public final class BookingPolicyMapper {

    private BookingPolicyMapper() {}

    public static BookingPolicyResponse toResponse(BookingPolicy entity) {
        return new BookingPolicyResponse(
                entity.getMaxBookingsPerDayPerClient(),
                entity.getMaxBookingsPerWeekForNewClient(),
                entity.getUpdatedAt()
        );
    }
}
