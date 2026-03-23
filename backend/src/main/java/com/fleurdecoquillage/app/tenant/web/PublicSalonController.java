package com.fleurdecoquillage.app.tenant.web;

import com.fleurdecoquillage.app.availability.app.AvailabilityService;
import com.fleurdecoquillage.app.availability.app.BlockedSlotService;
import com.fleurdecoquillage.app.availability.app.SlotAvailabilityService;
import com.fleurdecoquillage.app.availability.web.dto.BlockedSlotResponse;
import com.fleurdecoquillage.app.availability.web.dto.OpeningHourResponse;
import com.fleurdecoquillage.app.category.domain.Category;
import com.fleurdecoquillage.app.category.repo.CategoryRepository;
import com.fleurdecoquillage.app.multitenancy.TenantContext;
import com.fleurdecoquillage.app.tenant.app.TenantService;
import com.fleurdecoquillage.app.tenant.domain.TenantStatus;
import com.fleurdecoquillage.app.tenant.web.dto.PublicSalonResponse;
import com.fleurdecoquillage.app.tenant.web.mapper.TenantMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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

    public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository,
                                 AvailabilityService availabilityService, BlockedSlotService blockedSlotService,
                                 SlotAvailabilityService slotAvailabilityService) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
        this.availabilityService = availabilityService;
        this.blockedSlotService = blockedSlotService;
        this.slotAvailabilityService = slotAvailabilityService;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<PublicSalonResponse> getSalon(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .map(tenant -> {
                    // Set tenant context to query the correct schema
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        List<Category> categories = categoryRepository.findAll();
                        return ResponseEntity.ok(TenantMapper.toPublicResponse(tenant, categories));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/opening-hours")
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
}
