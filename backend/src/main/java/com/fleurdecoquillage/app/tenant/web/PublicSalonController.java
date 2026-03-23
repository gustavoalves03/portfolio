package com.fleurdecoquillage.app.tenant.web;

import com.fleurdecoquillage.app.category.domain.Category;
import com.fleurdecoquillage.app.category.repo.CategoryRepository;
import com.fleurdecoquillage.app.multitenancy.TenantContext;
import com.fleurdecoquillage.app.tenant.app.TenantService;
import com.fleurdecoquillage.app.tenant.web.dto.PublicSalonResponse;
import com.fleurdecoquillage.app.tenant.web.mapper.TenantMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/salon")
public class PublicSalonController {

    private final TenantService tenantService;
    private final CategoryRepository categoryRepository;

    public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<PublicSalonResponse> getSalon(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .filter(tenant -> tenant.getStatus() == com.fleurdecoquillage.app.tenant.domain.TenantStatus.ACTIVE)
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
}
