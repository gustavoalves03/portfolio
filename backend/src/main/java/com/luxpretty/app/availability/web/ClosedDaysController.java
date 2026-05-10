package com.luxpretty.app.availability.web;

import com.luxpretty.app.availability.app.ClosedDaysService;
import com.luxpretty.app.availability.web.dto.ClosedDayResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/pro/availability/closed-days")
public class ClosedDaysController {

    private final ClosedDaysService closedDaysService;

    public ClosedDaysController(ClosedDaysService closedDaysService) {
        this.closedDaysService = closedDaysService;
    }

    @GetMapping
    public List<ClosedDayResponse> getClosedDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return closedDaysService.getClosedDays(from, to).entrySet().stream()
                .map(e -> new ClosedDayResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ClosedDayResponse::date))
                .toList();
    }
}
