package com.prettyface.app.availability.web;

import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.availability.web.dto.OpeningHourRequest;
import com.prettyface.app.availability.web.dto.OpeningHourResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pro/opening-hours")
public class AvailabilityController {

    private final AvailabilityService service;
    private final SlotAvailabilityService slotAvailabilityService;

    public AvailabilityController(AvailabilityService service, SlotAvailabilityService slotAvailabilityService) {
        this.service = service;
        this.slotAvailabilityService = slotAvailabilityService;
    }

    @GetMapping
    public List<OpeningHourResponse> list() {
        return service.list();
    }

    @PutMapping
    public List<OpeningHourResponse> replaceAll(@RequestBody @Valid List<OpeningHourRequest> requests) {
        return service.replaceAll(requests);
    }

    @GetMapping("/available-slots")
    public List<SlotAvailabilityService.TimeSlot> getAvailableSlots(
            @RequestParam Long careId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slotAvailabilityService.getAvailableSlots(date, careId);
    }
}
