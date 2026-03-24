package com.prettyface.app.availability.web;

import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.web.dto.OpeningHourRequest;
import com.prettyface.app.availability.web.dto.OpeningHourResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/opening-hours")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    public List<OpeningHourResponse> list() {
        return service.list();
    }

    @PutMapping
    public List<OpeningHourResponse> replaceAll(@RequestBody @Valid List<OpeningHourRequest> requests) {
        return service.replaceAll(requests);
    }
}
