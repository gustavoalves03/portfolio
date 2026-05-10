package com.luxpretty.app.bookings.web;

import com.luxpretty.app.bookings.app.BookingPolicyService;
import com.luxpretty.app.bookings.web.dto.BookingPolicyResponse;
import com.luxpretty.app.bookings.web.dto.UpdateBookingPolicyRequest;
import com.luxpretty.app.bookings.web.mapper.BookingPolicyMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pro/booking-policy")
@PreAuthorize("hasRole('PRO')")
public class BookingPolicyController {

    private final BookingPolicyService service;

    public BookingPolicyController(BookingPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public BookingPolicyResponse get() {
        return BookingPolicyMapper.toResponse(service.getOrCreatePolicy());
    }

    @PutMapping
    public BookingPolicyResponse update(@Valid @RequestBody UpdateBookingPolicyRequest req) {
        return BookingPolicyMapper.toResponse(
                service.update(req.maxBookingsPerDayPerClient(), req.maxBookingsPerWeekForNewClient())
        );
    }
}
