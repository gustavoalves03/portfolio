package com.prettyface.app.bookings.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.bookings.web.dto.ClientBookingHistoryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client/me/bookings")
public class ClientBookingHistoryController {

    private final ClientBookingHistoryService service;

    public ClientBookingHistoryController(ClientBookingHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClientBookingHistoryResponse> getMyBookings(
            @RequestParam(defaultValue = "upcoming") String tab,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getId();
        return "past".equals(tab)
                ? service.getPast(userId)
                : service.getUpcoming(userId);
    }
}
