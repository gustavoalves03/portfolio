package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tenant.app.TenantReadinessService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.tenant.web.dto.PublishErrorResponse;
import com.prettyface.app.tenant.web.dto.TenantReadinessResponse;
import com.prettyface.app.tenant.web.dto.TenantResponse;
import com.prettyface.app.tenant.web.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pro/tenant")
public class TenantController {

    private final TenantService tenantService;
    private final TenantReadinessService readinessService;
    private final TenantRepository tenantRepository;

    public TenantController(TenantService tenantService,
                            TenantReadinessService readinessService,
                            TenantRepository tenantRepository) {
        this.tenantService = tenantService;
        this.readinessService = readinessService;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public TenantResponse getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return tenantService.getProfile(principal.getId());
    }

    @PutMapping
    public TenantResponse updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid UpdateTenantRequest request) {
        return tenantService.updateProfile(principal.getId(), request);
    }

    @GetMapping("/readiness")
    public ResponseEntity<TenantReadinessResponse> getReadiness(@AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        TenantContext.setCurrentTenant(tenant.getSlug());
        try {
            return ResponseEntity.ok(readinessService.getReadiness(tenant));
        } finally {
            TenantContext.clear();
        }
    }

    @PutMapping("/publish")
    @Transactional
    public ResponseEntity<?> publish(@AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        TenantContext.setCurrentTenant(tenant.getSlug());
        try {
            var missing = readinessService.getMissingConditions(tenant);
            if (!missing.isEmpty()) {
                return ResponseEntity.unprocessableEntity()
                        .body(new PublishErrorResponse("Salon cannot be published", missing));
            }
            tenant.setStatus(TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
            return ResponseEntity.ok().build();
        } finally {
            TenantContext.clear();
        }
    }

    @PutMapping("/unpublish")
    @Transactional
    public ResponseEntity<Void> unpublish(@AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = tenantRepository.findByOwnerId(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        tenant.setStatus(TenantStatus.DRAFT);
        tenantRepository.save(tenant);
        return ResponseEntity.ok().build();
    }
}
