package com.prettyface.app.tenant.web;

import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.tenant.web.dto.SalonCardResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/salons")
public class PublicDiscoveryController {

    private final TenantRepository tenantRepository;

    public PublicDiscoveryController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public List<SalonCardResponse> discover(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {

        List<Tenant> tenants;

        if (category != null && !category.isBlank()) {
            tenants = tenantRepository.findByStatusAndCategorySlugsContaining(TenantStatus.ACTIVE, category);
        } else if (q != null && !q.isBlank()) {
            tenants = tenantRepository.searchByKeyword(TenantStatus.ACTIVE, q.trim());
        } else {
            tenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
        }

        return tenants.stream().map(this::toCard).toList();
    }

    private String buildFullAddress(Tenant t) {
        StringBuilder sb = new StringBuilder();
        if (t.getAddressStreet() != null && !t.getAddressStreet().isBlank())
            sb.append(t.getAddressStreet());
        if (t.getAddressPostalCode() != null && !t.getAddressPostalCode().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(t.getAddressPostalCode());
        }
        if (t.getAddressCity() != null && !t.getAddressCity().isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(t.getAddressCity());
        }
        if (t.getAddressCountry() != null && !t.getAddressCountry().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(t.getAddressCountry());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private SalonCardResponse toCard(Tenant t) {
        String logoUrl = null;
        if (t.getLogoPath() != null) {
            int lastSlash = t.getLogoPath().lastIndexOf('/');
            String filename = lastSlash >= 0 ? t.getLogoPath().substring(lastSlash + 1) : t.getLogoPath();
            logoUrl = "/api/images/tenant/" + t.getId() + "/" + filename;
        }
        String desc = t.getDescription();
        if (desc != null) {
            // Strip HTML tags for card display
            desc = desc.replaceAll("<[^>]*>", "");
            if (desc.length() > 200) {
                desc = desc.substring(0, 200) + "...";
            }
        }
        String fullAddress = buildFullAddress(t);
        return new SalonCardResponse(t.getName(), t.getSlug(), desc, logoUrl, t.getCategoryNames(),
                t.getAddressCity(), fullAddress);
    }
}
