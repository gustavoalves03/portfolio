package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.BlockedSlotService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.availability.web.dto.BlockedSlotResponse;
import com.prettyface.app.availability.web.dto.OpeningHourResponse;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.bookings.web.dto.ClientBookingRequest;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.web.dto.PublicSalonResponse;
import com.prettyface.app.tenant.web.mapper.TenantMapper;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/salon")
public class PublicSalonController {

    private final TenantService tenantService;
    private final CategoryRepository categoryRepository;
    private final AvailabilityService availabilityService;
    private final BlockedSlotService blockedSlotService;
    private final SlotAvailabilityService slotAvailabilityService;
    private final CareBookingService careBookingService;
    private final UserRepository userRepository;
    private final ClientBookingHistoryService clientBookingHistoryService;

    public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository,
                                 AvailabilityService availabilityService, BlockedSlotService blockedSlotService,
                                 SlotAvailabilityService slotAvailabilityService,
                                 CareBookingService careBookingService, UserRepository userRepository,
                                 ClientBookingHistoryService clientBookingHistoryService) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
        this.availabilityService = availabilityService;
        this.blockedSlotService = blockedSlotService;
        this.slotAvailabilityService = slotAvailabilityService;
        this.careBookingService = careBookingService;
        this.userRepository = userRepository;
        this.clientBookingHistoryService = clientBookingHistoryService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicSalonResponse> getSalon(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    // Set tenant context to query the correct schema
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        List<Category> categories = categoryRepository.findAllWithCares();
                        return ResponseEntity.ok(TenantMapper.toPublicResponse(tenant, categories));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/opening-hours")
    public ResponseEntity<List<OpeningHourResponse>> getOpeningHours(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        return ResponseEntity.ok(availabilityService.list());
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/blocked-slots")
    public ResponseEntity<List<BlockedSlotResponse>> getBlockedSlots(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        return ResponseEntity.ok(blockedSlotService.listFuture());
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/available-slots")
    public ResponseEntity<List<SlotAvailabilityService.TimeSlot>> getAvailableSlots(
            @PathVariable String slug,
            @RequestParam Long careId,
            @RequestParam LocalDate date) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        return ResponseEntity.ok(slotAvailabilityService.getAvailableSlots(date, careId));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{slug}/book")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientBookingResponse book(@PathVariable String slug,
                                       @Valid @RequestBody ClientBookingRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        var tenant = tenantService.findBySlug(slug)
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon not found"));

        // Resolve users from public schema BEFORE setting tenant context
        User client = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User owner = userRepository.findById(tenant.getOwnerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon owner not found"));
        String salonName = tenant.getName();

        ClientBookingResponse result;
        TenantContext.setCurrentTenant(slug);
        try {
            result = careBookingService.createClientBooking(client, owner, salonName, request);
        } finally {
            TenantContext.clear();
        }

        // Mirror write in public schema (after TenantContext cleared → APPUSER)
        clientBookingHistoryService.createMirror(client, result, slug, salonName);

        return result;
    }
}
