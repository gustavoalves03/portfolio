package com.luxpretty.app.feature.web;

import com.luxpretty.app.common.error.ResourceNotFoundException;
import com.luxpretty.app.feature.app.FeatureFlagService;
import com.luxpretty.app.feature.domain.FeatureKey;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tenants")
public class AdminFeatureFlagController {

    private final FeatureFlagService service;
    private final TenantRepository tenantRepository;

    public AdminFeatureFlagController(FeatureFlagService service, TenantRepository tenantRepository) {
        this.service = service;
        this.tenantRepository = tenantRepository;
    }

    public record OverrideRequest(boolean enabled) {}

    @PutMapping("/{tenantId}/features/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void override(@PathVariable Long tenantId,
                         @PathVariable FeatureKey key,
                         @RequestBody OverrideRequest req) {
        Tenant target = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(target.getSlug());
        try {
            service.overrideForTenant(key, req.enabled());
        } finally {
            if (previous != null) {
                TenantContext.setCurrentTenant(previous);
            } else {
                TenantContext.clear();
            }
        }
    }
}
