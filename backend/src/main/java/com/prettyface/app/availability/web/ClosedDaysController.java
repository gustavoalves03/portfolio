package com.prettyface.app.availability.web;

import com.prettyface.app.availability.app.ClosedDaysService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pro/availability/closed-days")
public class ClosedDaysController {

    private final ClosedDaysService closedDaysService;

    public ClosedDaysController(ClosedDaysService closedDaysService) {
        this.closedDaysService = closedDaysService;
    }

    @GetMapping
    public List<LocalDate> getClosedDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return closedDaysService.getClosedDays(from, to).stream()
                .sorted()
                .toList();
    }
}
