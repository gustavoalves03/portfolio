package com.fleurdecoquillage.app.bookings.web;

import com.fleurdecoquillage.app.bookings.app.CareBookingService;
import com.fleurdecoquillage.app.bookings.domain.CareBookingStatus;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingDetailedResponse;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingRequest;
import com.fleurdecoquillage.app.bookings.web.dto.CareBookingResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/bookings")
public class CareBookingController {

    private final CareBookingService service;
    public CareBookingController(CareBookingService service) { this.service = service; }

    /**
     * List bookings with basic information (backward compatible)
     */
    @GetMapping
    public Page<CareBookingResponse> list(Pageable pageable) { return service.list(pageable); }

    /**
     * List bookings with detailed information and optional filters
     *
     * @param status Optional filter by booking status (PENDING, CONFIRMED)
     * @param from Optional filter by start date (ISO-8601 format, e.g. 2025-01-01T00:00:00Z)
     * @param to Optional filter by end date (ISO-8601 format, e.g. 2025-01-31T23:59:59Z)
     * @param userId Optional filter by user ID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of detailed booking responses with user and care information
     *
     * Example: GET /api/bookings/detailed?status=PENDING&from=2025-01-01T00:00:00Z&page=0&size=10&sort=createdAt,desc
     */
    @GetMapping("/detailed")
    public Page<CareBookingDetailedResponse> listDetailed(
            @RequestParam(required = false) CareBookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long userId,
            Pageable pageable
    ) {
        return service.listDetailed(status, from, to, userId, pageable);
    }

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

