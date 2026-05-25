package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.availability.app.AvailabilityService;
import com.luxpretty.app.availability.app.BlockedSlotService;
import com.luxpretty.app.availability.app.ClosedDaysService;
import com.luxpretty.app.availability.app.HolidayAvailabilityService;
import com.luxpretty.app.availability.app.SlotAvailabilityService;
import com.luxpretty.app.availability.web.dto.BlockedSlotResponse;
import com.luxpretty.app.bookings.web.dto.SlotWithEmployees;
import com.luxpretty.app.availability.web.dto.ClosedDayResponse;
import com.luxpretty.app.availability.web.dto.OpeningHourResponse;
import com.luxpretty.app.bookings.app.CareBookingService;
import com.luxpretty.app.bookings.app.ClientBookingHistoryService;
import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.web.dto.ClientBookingRequest;
import com.luxpretty.app.bookings.web.dto.ClientBookingResponse;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.employee.web.dto.EmployeeSlimResponse;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.post.app.PostService;
import com.luxpretty.app.post.web.dto.PostResponse;
import com.luxpretty.app.tenant.app.SalonPreviewTokenService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.web.dto.PublicSalonResponse;
import com.luxpretty.app.tenant.web.mapper.TenantMapper;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final HolidayAvailabilityService holidayAvailabilityService;
    private final ClosedDaysService closedDaysService;
    private final CareBookingService careBookingService;
    private final UserRepository userRepository;
    private final ClientBookingHistoryService clientBookingHistoryService;
    private final EmployeeService employeeService;
    private final PostService postService;
    private final SalonPreviewTokenService previewTokenService;

    public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository,
                                 AvailabilityService availabilityService, BlockedSlotService blockedSlotService,
                                 SlotAvailabilityService slotAvailabilityService,
                                 HolidayAvailabilityService holidayAvailabilityService,
                                 ClosedDaysService closedDaysService,
                                 CareBookingService careBookingService, UserRepository userRepository,
                                 ClientBookingHistoryService clientBookingHistoryService,
                                 EmployeeService employeeService, PostService postService,
                                 SalonPreviewTokenService previewTokenService) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
        this.availabilityService = availabilityService;
        this.blockedSlotService = blockedSlotService;
        this.slotAvailabilityService = slotAvailabilityService;
        this.holidayAvailabilityService = holidayAvailabilityService;
        this.closedDaysService = closedDaysService;
        this.careBookingService = careBookingService;
        this.userRepository = userRepository;
        this.clientBookingHistoryService = clientBookingHistoryService;
        this.employeeService = employeeService;
        this.postService = postService;
        this.previewTokenService = previewTokenService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicSalonResponse> getSalon(
            @PathVariable String slug,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String preview) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> canViewStorefront(tenant, principal, preview))
                .map(tenant -> {
                    // Set tenant context to query the correct schema
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        List<Category> categories = categoryRepository.findAllWithCaresFull();
                        return ResponseEntity.ok(TenantMapper.toPublicResponse(tenant, categories));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Storefront access policy:
     * - ACTIVE salons are visible to everyone.
     * - DRAFT salons are visible to their authenticated owner OR via a valid preview token.
     * - SUSPENDED / DELETED salons are not visible to anyone.
     */
    private boolean canViewStorefront(
            com.luxpretty.app.tenant.domain.Tenant tenant,
            UserPrincipal principal,
            String previewToken) {
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return true;
        }
        if (tenant.getStatus() == TenantStatus.DRAFT) {
            if (principal != null && tenant.getOwnerId().equals(principal.getId())) {
                return true;
            }
            if (previewToken != null
                    && previewTokenService.isValidForTenant(previewToken, tenant.getId())) {
                return true;
            }
        }
        return false;
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

    @GetMapping("/{slug}/closed-days")
    public ResponseEntity<List<ClosedDayResponse>> getClosedDays(
            @PathVariable String slug,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        List<ClosedDayResponse> dates = closedDaysService.getClosedDays(from, to)
                                .entrySet().stream()
                                .map(e -> new ClosedDayResponse(e.getKey(), e.getValue()))
                                .sorted(java.util.Comparator.comparing(ClosedDayResponse::date))
                                .toList();
                        return ResponseEntity.ok(dates);
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
                    // Check if salon is closed for a public holiday
                    boolean closedOnHolidays = Boolean.TRUE.equals(tenant.getClosedOnHolidays());
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        if (holidayAvailabilityService.isClosedForHoliday(
                                date, tenant.getAddressCountry(), closedOnHolidays)) {
                            return ResponseEntity.ok(List.<SlotAvailabilityService.TimeSlot>of());
                        }
                        return ResponseEntity.ok(slotAvailabilityService.getAvailableSlots(date, careId));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/slots/by-care")
    public ResponseEntity<List<SlotWithEmployees>> getAvailableSlotsByCareWithEmployees(
            @PathVariable String slug,
            @RequestParam Long careId,
            @RequestParam LocalDate date) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        return ResponseEntity.ok(
                                slotAvailabilityService.getAvailableSlotsForCareWithEmployees(date, careId));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/employees")
    public List<EmployeeSlimResponse> listEmployeesForCare(
            @PathVariable String slug,
            @RequestParam Long careId) {
        // Resolve tenant to verify it exists
        tenantService.findBySlug(slug);
        TenantContext.setCurrentTenant(slug);
        try {
            return employeeService.listForCare(careId);
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/{slug}/posts")
    public Page<PostResponse> listPosts(@PathVariable String slug, Pageable pageable) {
        tenantService.findBySlug(slug);
        TenantContext.setCurrentTenant(slug);
        try {
            return postService.listPublicPaged(pageable);
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/{slug}/bookings/{bookingId}/cancel")
    public ResponseEntity<Void> cancelBooking(@PathVariable String slug,
                                               @PathVariable Long bookingId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        var tenant = tenantService.findBySlug(slug)
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon not found"));

        // Cancel in tenant schema
        TenantContext.setCurrentTenant(slug);
        try {
            CareBooking booking = careBookingService.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

            // Security: only the booking owner can cancel
            if (!booking.getUser().getId().equals(principal.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your booking");
            }

            // Cannot cancel past bookings
            if (LocalDate.now().isAfter(booking.getAppointmentDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a past appointment");
            }

            if (booking.getStatus() == CareBookingStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking already cancelled");
            }

            careBookingService.cancelBooking(bookingId);
        } finally {
            TenantContext.clear();
        }

        // Update mirror in shared schema
        clientBookingHistoryService.updateMirrorStatus(slug, bookingId, "CANCELLED");

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{slug}/book")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientBookingResponse book(@PathVariable String slug,
                                       @Valid @RequestBody ClientBookingRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        // Booking requires authentication. Spring's security filter is
        // permissive on /api/salon/** (public read), so reject here explicitly
        // rather than NPE on principal access.
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required to book");
        }

        // Client booking is for "client mode" only. A PRO/EMPLOYEE/ADMIN with an
        // active tenant context must switch back to client mode first.
        Object activeTenantId = principal.getAttributes() == null
                ? null
                : principal.getAttributes().get("activeTenantId");
        if (activeTenantId != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Switch to client mode to book an appointment");
        }

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

        // Mirror write in the shared application schema after TenantContext is cleared.
        clientBookingHistoryService.createMirror(client, result, slug, salonName);

        return result;
    }
}
