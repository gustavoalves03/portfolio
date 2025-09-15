package com.fleurdecoquillage.app.bookings.web;

import com.fleurdecoquillage.app.bookings.app.CareBookingService;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingRequest;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class CareBookingController {

    private final CareBookingService service;
    public CareBookingController(CareBookingService service) { this.service = service; }

    @GetMapping
    public Page<CareBookingResponse> list(Pageable pageable) { return service.list(pageable); }

    @GetMapping("/{id}")
    public CareBookingResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public CareBookingResponse create(@RequestBody @Valid CareBookingRequest req) { return service.create(req); }

    @PutMapping("/{id}")
    public CareBookingResponse update(@PathVariable Long id, @RequestBody @Valid CareBookingRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}

