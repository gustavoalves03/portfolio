package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.availability.app.AvailabilityService;
import com.prettyface.app.availability.app.BlockedSlotService;
import com.prettyface.app.availability.app.HolidayAvailabilityService;
import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.availability.web.dto.BlockedSlotResponse;
import com.prettyface.app.availability.web.dto.OpeningHourResponse;
import com.prettyface.app.bookings.app.CareBookingService;
import com.prettyface.app.bookings.app.ClientBookingHistoryService;
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.web.dto.ClientBookingRequest;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.employee.app.EmployeeService;
import com.prettyface.app.employee.web.dto.EmployeeSlimResponse;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.post.app.PostService;
import com.prettyface.app.post.web.dto.PostResponse;
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
    private final HolidayAvailabilityService holidayAvailabilityService;
    private final CareBookingService careBookingService;
    private final UserRepository userRepository;
    private final ClientBookingHistoryService clientBookingHistoryService;
    private final EmployeeService employeeService;
    private final PostService postService;

    public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository,
                                 AvailabilityService availabilityService, BlockedSlotService blockedSlotService,
                                 SlotAvailabilityService slotAvailabilityService,
                                 HolidayAvailabilityService holidayAvailabilityService,
                                 CareBookingService careBookingService, UserRepository userRepository,
                                 ClientBookingHistoryService clientBookingHistoryService,
                                 EmployeeService employeeService, PostService postService) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
        this.availabilityService = availabilityService;
        this.blockedSlotService = blockedSlotService;
        this.slotAvailabilityService = slotAvailabilityService;
        this.holidayAvailabilityService = holidayAvailabilityService;
        this.careBookingService = careBookingService;
        this.userRepository = userRepository;
        this.clientBookingHistoryService = clientBookingHistoryService;
        this.employeeService = employeeService;
        this.postService = postService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicSalonResponse> getSalon(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
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
    public List<PostResponse> listPosts(@PathVariable String slug) {
        tenantService.findBySlug(slug);
        TenantContext.setCurrentTenant(slug);
        try {
            return postService.listAll();
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
