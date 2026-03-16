package com.fleurdecoquillage.app.tenant.web;

import com.fleurdecoquillage.app.auth.UserPrincipal;
import com.fleurdecoquillage.app.tenant.web.dto.TenantResponse;
import com.fleurdecoquillage.app.tenant.web.dto.UpdateTenantRequest;
import com.fleurdecoquillage.app.tenant.app.TenantService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pro/tenant")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
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
}
