package com.prettyface.app.bookings.web;

import com.prettyface.app.bookings.app.AnalyticsService;
import com.prettyface.app.bookings.web.dto.AnalyticsResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pro/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public AnalyticsResponse getAnalytics(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long careId) {
        return service.compute(period, employeeId, careId);
    }
}
