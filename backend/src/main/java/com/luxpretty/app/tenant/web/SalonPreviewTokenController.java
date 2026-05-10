package com.luxpretty.app.tenant.web;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.tenant.app.SalonPreviewTokenService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.tenant.domain.SalonPreviewToken;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.web.dto.PreviewTokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pro/salon/preview-tokens")
public class SalonPreviewTokenController {

    private final SalonPreviewTokenService tokenService;
    private final TenantService tenantService;

    public SalonPreviewTokenController(SalonPreviewTokenService tokenService, TenantService tenantService) {
        this.tokenService = tokenService;
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<PreviewTokenResponse> create(@AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = requireTenant(principal);
        SalonPreviewToken minted = tokenService.create(tenant.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(minted, tenant.getSlug()));
    }

    @GetMapping
    public ResponseEntity<List<PreviewTokenResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = requireTenant(principal);
        List<PreviewTokenResponse> tokens = tokenService.listByTenant(tenant.getId()).stream()
                .map(t -> toResponse(t, tenant.getSlug()))
                .toList();
        return ResponseEntity.ok(tokens);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        Tenant tenant = requireTenant(principal);
        tokenService.revoke(id, tenant.getId());
        return ResponseEntity.noContent().build();
    }

    private Tenant requireTenant(UserPrincipal principal) {
        return tenantService.findByOwnerId(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenant for user"));
    }

    private static PreviewTokenResponse toResponse(SalonPreviewToken t, String slug) {
        String shareUrl = "/salon/" + slug + "?preview=" + t.getToken();
        return new PreviewTokenResponse(
                t.getId(),
                t.getToken(),
                shareUrl,
                t.getCreatedAt(),
                t.getExpiresAt(),
                t.getRevokedAt()
        );
    }
}
